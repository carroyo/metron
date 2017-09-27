package org.apache.metron.common.field.validation;

import com.fasterxml.jackson.core.type.TypeReference;
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
    HashMap<String, String> cim = new HashMap();
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
        cim.put( "src_ip", "src_ip");
        cim.put( "1dst_ip", "1dst_ip");
        try {
        InputStream commonInputStream = openInputStream(taxonomyCommonDir);
        HashMap<String, String> cim2 = JSONUtils.INSTANCE.load(commonInputStream, new TypeReference<HashMap<String, String>>() {
        });
        cim=cim2;

        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException("CIMValidator taxonomy reading Error: " + e.getMessage(), e);
        }
    }

        @Override
    public Predicate<Object> getPredicate() {
        return s -> cim.containsValue(s) ;
    }

    @Override
    protected boolean isNonExistentOk() {
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
