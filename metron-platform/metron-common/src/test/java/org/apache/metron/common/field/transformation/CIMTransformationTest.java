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

import com.google.common.collect.Iterables;
import org.adrianwalker.multilinestring.Multiline;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.metron.common.configuration.FieldTransformer;
import org.apache.metron.common.configuration.SensorParserConfig;
import org.apache.metron.stellar.dsl.Context;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class CIMTransformationTest {


  /**
   {
    "fieldTransformations" : [
          {
            "input" : "any"
          , "transformation" : "CIM"

          }
                      ]
   }
   */
  @Multiline
  public static String CIMtransformConfig;
  @Test
  public void testCIM() throws Exception {
    SensorParserConfig c = SensorParserConfig.fromBytes(Bytes.toBytes(CIMtransformConfig));
    FieldTransformer handler = Iterables.getFirst(c.getFieldTransformations(), null);
    {
      JSONObject input = new JSONObject(new HashMap<String, Object>() {{
        put("srcip", "foo");
        put("dstip","bar");
        put("field","other");
      }});
      handler.transformAndUpdate(input, Context.EMPTY_CONTEXT());

      Assert.assertTrue(input.containsKey("src_ip"));
      Assert.assertFalse(input.containsKey("srcip"));
      Assert.assertTrue(input.containsKey("field"));
      Assert.assertTrue(input.containsKey("dst_ip"));

    }
    {
      JSONObject input = new JSONObject(new HashMap<String, Object>() {{
        put("srcip", "foo");
        put("dst_ip", "bar");
      }});
      handler.transformAndUpdate(input, Context.EMPTY_CONTEXT());
      Assert.assertTrue(input.containsKey("src_ip"));
      Assert.assertTrue(input.containsKey("dst_ip"));
    }
    {
      JSONObject input = new JSONObject(new HashMap<String, Object>() {{
        put("src_ipaddr", "bar");
        put("dsti_ip", "foo");
      }});
      handler.transformAndUpdate(input, Context.EMPTY_CONTEXT());
      Assert.assertFalse(input.containsKey("dst_ip"));
      Assert.assertTrue(input.containsKey("src_ip"));
    }
  }
}
