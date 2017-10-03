/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.metron.common.field.transformation;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.metron.common.utils.JSONUtils;
import org.apache.metron.stellar.dsl.*;
import org.apache.metron.stellar.dsl.functions.resolver.FunctionResolver;
import org.apache.metron.stellar.common.StellarPredicateProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Stellar( namespace = "PARSER_STELLAR_TRANSFORM"
        , name="CIM"
        , description=" Taxonomy fields adjustments"
        , params = {}
        , returns = "message"
)
public class CIMTransformation implements FieldTransformation, StellarFunction {

    protected String taxonomyCommonDir = "/taxonomy/taxonomy.json";
    HashMap<String, ArrayList<String>> cim = new HashMap();

    public void initialize2() {

        try {
            InputStream commonInputStream = openInputStream(taxonomyCommonDir);
            HashMap<String, ArrayList<String>> cim2 = JSONUtils.INSTANCE.load(commonInputStream, new TypeReference<HashMap<String, ArrayList<String>>>() {
            });
            cim=cim2;

        } catch (Throwable e) {
            throw new RuntimeException("CIMTransformation taxonomy reading Error: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> map( Map<String, Object> input
            , final List<String> outputFields
            , LinkedHashMap<String, Object> fieldMappingConfig
            , Context context
            , Map<String, Object>... sensorConfig
    ) {

        initialize2();
        Set<String> cimFields=cim.keySet();
        Map<String, Object> ret = new HashMap<>();

        for(Map.Entry<String, Object> entry : input.entrySet()) {
            String k = entry.getKey();
            String v = (String) entry.getValue();
            if( cimFields.contains(k)) {ret.put(k,v);continue;}
            for (Map.Entry<String, ArrayList<String>> entryCim : cim.entrySet())
            {
                if(entryCim.getValue().contains(k)){
                    ret.put(entryCim.getKey(),v);
                    ret.put(k,null);
                }
            }

        }

        return ret;
    }
    @Override
    public Object apply(List<Object> objects, Context context) throws ParseException {
        return true;
    }

    @Override
    public void initialize(Context context) {
        try {
            InputStream commonInputStream = openInputStream(taxonomyCommonDir);
            HashMap<String, ArrayList<String>> cim2 = JSONUtils.INSTANCE.load(commonInputStream, new TypeReference<HashMap<String, ArrayList<String>>>() {
            });
            cim=cim2;

        } catch (Throwable e) {
            throw new RuntimeException("CIMTransformation taxonomy reading Error: " + e.getMessage(), e);
        }

    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    public InputStream openInputStream(String streamName) throws IOException {
        FileSystem fs = FileSystem.get(new Configuration());
        Path path = new Path(streamName);
        if(fs.exists(path)) {
            return fs.open(path);
        } else {
            return getClass().getResourceAsStream(streamName);
        }
    }
}
