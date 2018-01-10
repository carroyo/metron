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

package org.apache.metron.common.field.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.vfs2.provider.sftp.TrustEveryoneUserInfo;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.metron.common.field.validation.SimpleValidation;
import org.apache.metron.stellar.dsl.Context;
import org.json.simple.JSONObject;
import org.apache.metron.common.utils.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.math.Ordering;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.HashMap;

public class CIMValidation extends SimpleValidation {

    protected static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected String taxonomyCommonDir = "/taxonomy/taxonomy.json";
    HashMap<String, ArrayList<String>> cim = new HashMap();
    JSONObject jsonObject;
    @Override
    public boolean isValid( Map<String, Object> input
            , Map<String, Object> validationConfig
            , Map<String, Object> globalConfig
            , Context context
    )
    {
        Predicate<Object> predicate = getPredicate();
        if(isNonExistentOk()) {
            for (Object o : input.keySet()) {
                if (o != null && !predicate.test(o.toString())) {
                    return false;
                }
            }
        }
        else {
            for (Object o : input.keySet()) {
                if (o == null || !predicate.test(o.toString())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean test(List<Object> input) {
        Predicate<Object> predicate = getPredicate();
        for(Object o : input) {
            if(o == null || !predicate.test(o)){
                return false;
            }
        }
        return true;
    }

    @Override
    public void initialize(Map<String, Object> validationConfig, Map<String, Object> globalConfig) {

        try {
        InputStream commonInputStream = openInputStream(taxonomyCommonDir);
        HashMap<String, ArrayList<String>> cim2 = JSONUtils.INSTANCE.load(commonInputStream, new TypeReference<HashMap<String, ArrayList<String>>>() {
        });
        cim=cim2;

        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException("CIMValidator taxonomy reading Error: " + e.getMessage(), e);
        }
    }

        @Override
    public Predicate<Object> getPredicate() {
        return s -> arraysContain(s);
    }


    @Override
    protected boolean isNonExistentOk() {
        return false;
    }

    protected boolean arraysContain(Object s){
        for(ArrayList value : cim.values()) {
            if(value.contains(s)) return value.contains(s);
        }
        return false;
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
