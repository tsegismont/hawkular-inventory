/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.inventory.json;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.RelativePath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.json.mixins.TenantlessCanonicalPathMixin;
import org.hawkular.inventory.json.mixins.TenantlessRelativePathMixin;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
public class SerializationTest {

    private ObjectMapper mapper;

    @Before
    public void setup() {
        JsonFactory f = new JsonFactory();

        mapper = new ObjectMapper(f);

        InventoryJacksonConfig.configure(mapper);
    }

    @Test
    public void testCanonicalPath() throws Exception {
        test(CanonicalPath.fromString("/t;t/e;e/r;r"));
    }

    @Test
    public void testTenantlessCanonicalPath() throws Exception {
        mapper.addMixIn(CanonicalPath.class, TenantlessCanonicalPathMixin.class);
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));
        DetypedPathDeserializer.setCurrentEntityType(Resource.class);
        test(CanonicalPath.fromString("/t;t/e;e/r;r"));
    }

    @Test
    public void testRelativePath() throws Exception {
        test(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"), Metric.class));
    }

    @Test
    public void testTenantlessRelativePath() throws Exception {
        mapper.addMixIn(RelativePath.class, TenantlessRelativePathMixin.class);
        DetypedPathDeserializer.setCurrentEntityType(Metric.class);
        DetypedPathDeserializer.setCurrentRelativePathOrigin(CanonicalPath.fromString("/t;t/e;e/r;r"));
        test(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"), Metric.class)
        );

        //the test above doesn't test for deserializing a de-typed path.
        RelativePath rp = deserialize("\"../g\"", RelativePath.class);
        Assert.assertEquals(RelativePath.fromPartiallyUntypedString("../g", CanonicalPath.fromString("/t;t/e;e/r;r"),
                Metric.class), rp);
    }

    @Test
    public void testTenant() throws Exception {
        Tenant t = new Tenant(CanonicalPath.fromString("/t;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(t);
    }

    @Test
    public void testDetypedTenant() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(null);

        Tenant t = new Tenant(CanonicalPath.fromString("/t;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});
        String ser = "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}";

        testDetyped(t, ser);
    }

    @Test
    public void testEnvironment() throws Exception {
        Environment env = new Environment(CanonicalPath.fromString("/t;t/e;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(env);
    }

    @Test
    public void testDetypedEnvironment() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Environment env = new Environment(CanonicalPath.fromString("/t;t/e;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(env, "{\"path\":\"/e;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(env, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(env, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testResourceType() throws Exception {
        ResourceType rt = new ResourceType(CanonicalPath.fromString("/t;t/rt;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(rt);
    }

    @Test
    public void testDetypedResourceType() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        ResourceType rt = new ResourceType(CanonicalPath.fromString("/t;t/rt;c"), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(rt, "{\"path\":\"/t;t/rt;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(rt, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(rt, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testMetricType() throws Exception {
        MetricType mt = new MetricType(CanonicalPath.fromString("/t;t/mt;c"), MetricUnit.BYTES, MetricDataType.GAUGE,
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        test(mt);
    }

    @Test
    public void testDetypedMetricType() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        MetricType mt = new MetricType(CanonicalPath.fromString("/t;t/mt;c"), MetricUnit.BYTES, MetricDataType.GAUGE,
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        testDetyped(mt, "{\"path\":\"/t;t/mt;c\",\"properties\":{\"a\":\"b\"},\"unit\":\"BYTES\"}");
        testDetyped(mt, "{\"path\":\"/t;t/c\",\"properties\":{\"a\":\"b\"},\"unit\":\"BYTES\"}");
        testDetyped(mt, "{\"path\":\"/c\",\"properties\":{\"a\":\"b\"},\"unit\":\"BYTES\"}");
    }

    @Test
    public void testFeed() throws Exception {
        Feed f = new Feed(CanonicalPath.fromString("/t;t/e;e/f;c"),
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        test(f);
    }

    @Test
    public void testDetypedFeed() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Feed f = new Feed(CanonicalPath.fromString("/t;t/e;e/f;c"),
                new HashMap<String, Object>() {{
                    put("a", "b");
                }});

        testDetyped(f, "{\"path\":\"/t;t/e;e/f;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(f, "{\"path\":\"/t;t/e/c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(f, "{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testResourceInEnvironment() throws Exception {
        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/r;c"), new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(r);
    }

    @Test
    public void testDetypedResourceInEvironment() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/r;c"), new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(r, "{\"path\":\"/t;t/e;e/r;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(r, "{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testMetricInEnvironment() throws Exception {
        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/m;c"), new MetricType(
                CanonicalPath.fromString("/t;t/mt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(m);
    }

    @Test
    public void testDetypedMetricInEnvironment() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/m;c"), new MetricType(
                CanonicalPath.fromString("/t;t/mt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(m, "{\"path\":\"/t;t/e;e/m;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(m, "{\"path\":\"/e/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testResourceInFeed() throws Exception {
        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/f;f/r;c"), new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(r);
    }

    @Test
    public void testDetypedResourceInFeed() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Resource r = new Resource(CanonicalPath.fromString("/t;t/e;e/f;f/r;c"), new ResourceType(
                CanonicalPath.fromString("/t;t/rt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(r, "{\"path\":\"/t;t/e;e/f;f/r;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(r, "{\"path\":\"/e/f/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testMetricInFeed() throws Exception {
        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/f;f/m;c"), new MetricType(
                CanonicalPath.fromString("/t;t/mt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        test(m);
    }

    @Test
    public void testDetypedMetricInFeed() throws Exception {
        DetypedPathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.fromString("/t;t"));

        Metric m = new Metric(CanonicalPath.fromString("/t;t/e;e/f;f/m;c"), new MetricType(
                CanonicalPath.fromString("/t;t/mt;k")), new HashMap<String, Object>() {{
            put("a", "b");
        }});

        testDetyped(m, "{\"path\":\"/t;t/e;e/f;f/m;c\",\"properties\":{\"a\":\"b\"}}");
        testDetyped(m, "{\"path\":\"/e/f/c\",\"properties\":{\"a\":\"b\"}}");
    }

    @Test
    public void testOperationType() throws Exception {
        OperationType ot = new OperationType(CanonicalPath.fromString("/t;t/rt;rt/ot;ot"));

        test(ot);
    }

    @Test
    public void testStructuredData() throws Exception {
        test(StructuredData.get().bool(true));
        test(StructuredData.get().integral(42L));
        test(StructuredData.get().floatingPoint(1.D));
        test(StructuredData.get().string("answer"));
        test(StructuredData.get().list().addBool(true).build());
        test(StructuredData.get().list().addList().addBool(true).addIntegral(2L).closeList().build());
        test(StructuredData.get().list().addMap().putIntegral("answer", 42L).closeMap().build());
        test(StructuredData.get().map().putBool("yes", true).build());
        test(StructuredData.get().map().putList("answer-list").addIntegral(42L).closeList().build());
    }

    @Test
    public void testDataEntity() throws Exception {
        test(new DataEntity(CanonicalPath.of().tenant("t").environment("e").resource("r").get(),
                Resources.DataRole.connectionConfiguration,
                StructuredData.get().list().addIntegral(1).addIntegral(2).build(), null));
    }

    @Test
    public void testEntityBlueprint() throws Exception {
        String ser = serialize(new Environment.Blueprint("env",
                new HashMap<String, Object>() {{
                    put("key", "value");
                }},
                new HashMap<String, Set<CanonicalPath>>() {{
                    put("kachna", new HashSet<>(Arrays.asList(CanonicalPath.of().tenant("t").get())));
                }},
                null));

        Environment.Blueprint b = deserialize(ser, Environment.Blueprint.class);

        Assert.assertEquals("env", b.getId());
        Assert.assertEquals(Collections.<String, Set<CanonicalPath>>emptyMap(), b.getIncomingRelationships());
        Assert.assertEquals(Collections.singleton(CanonicalPath.of().tenant("t").get()),
                b.getOutgoingRelationships().get("kachna"));
        Assert.assertEquals("value", b.getProperties().get("key"));
    }

    private void testDetyped(Entity<?, ?> orig, String serialized) throws Exception {
        DetypedPathDeserializer.setCurrentEntityType(orig.getClass());
        mapper.addMixIn(CanonicalPath.class, TenantlessCanonicalPathMixin.class);

        Assert.assertEquals(orig, deserialize(serialized, orig.getClass()));
    }

    private void test(Object o) throws Exception {
        Class<?> cls = o.getClass();

        Object o2 = deserialize(serialize(o), cls);

        Assert.assertEquals(o, o2);
    }

    private String serialize(Object object) throws IOException {
        StringWriter out = new StringWriter();

        JsonGenerator gen = mapper.getFactory().createGenerator(out);

        gen.writeObject(object);

        gen.close();

        out.close();

        return out.toString();
    }

    private <T> T deserialize(String json, Class<T> type) throws Exception {
        JsonParser parser = mapper.getFactory().createParser(json);

        return parser.readValueAs(type);
    }
}
