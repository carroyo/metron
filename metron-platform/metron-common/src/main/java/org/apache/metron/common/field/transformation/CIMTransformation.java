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

public class CIMTransformation implements FieldTransformation {

    protected String taxonomyCommonDir = "/taxonomy/taxonomy.json";
    HashMap<String, ArrayList<String>> cim = new HashMap();

    public void initialize() {

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

        initialize();
        Set<String> cimFields=cim.keySet();
        Map<String, Object> ret = new HashMap<>();

        input.forEach( (k,v) -> {
            if( cimFields.contains(k)) {ret.put(k,v);return;}
            for (Map.Entry<String, ArrayList<String>> entry : cim.entrySet())
            {
                if(entry.getValue().contains(k)){
                    ret.put(entry.getKey(),v);
                    ret.put(k,null);
                }
            }

        });

        return ret;
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
