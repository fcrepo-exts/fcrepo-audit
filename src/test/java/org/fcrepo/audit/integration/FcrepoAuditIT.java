/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.audit.integration;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.fcrepo.audit.AuditProperties.OBJECT_ADD;
import static org.fcrepo.audit.AuditProperties.PREMIS_TYPE;
import static org.fcrepo.kernel.api.RdfLexicon.LDP_NAMESPACE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Integration tests for the audit system
 *
 * @author acoburn
 * @author escowles
 * @since 2015-04-23
 */
@ContextConfiguration({"/spring-test/test-container.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class FcrepoAuditIT {

    private static Logger logger = LoggerFactory.getLogger(FcrepoAuditIT.class);

    protected static final int SERVER_PORT = Integer.parseInt(System.getProperty("fcrepo.dynamic.test.port", "8080"));

    protected static final String HOSTNAME = "localhost";

    protected static final String serverAddress = "http://" + HOSTNAME + ":"
            + SERVER_PORT;

    private static HttpClient client = HttpClientBuilder.create().useSystemProperties().build();

    @Test
    public void testCreateNode() throws Exception {
        final List<String> beforeEvents = findAuditEvents();
        final HttpPost createNode = new HttpPost(serverAddress);
        final HttpResponse response = client.execute(createNode);

        awaitEvent();
        final List<String> afterEvents = findAuditEvents();
        afterEvents.removeAll(beforeEvents);
        assertEquals(1, afterEvents.size());

        final Model m = getModel(afterEvents.get(0));
        assertTrue(m.contains(null, createProperty(PREMIS_TYPE), createResource(OBJECT_ADD)));
    }

    private static List<String> findAuditEvents() throws IOException {
        final Model m = getModel(serverAddress + "/audit");
        final List<String> events = new ArrayList<>();
        final NodeIterator nodes = m.listObjectsOfProperty(createProperty(LDP_NAMESPACE + "contains"));
        while ( nodes.hasNext() ) {
            events.add(nodes.next().asResource().getURI());
        }
        return events;
    }
    private static Model getModel(final String uri) throws IOException {
        final HttpGet get = new HttpGet(uri);
        final HttpResponse response = client.execute(get);
        final Model m = createDefaultModel();
        return m.read(response.getEntity().getContent(), null, "TURTLE");
    }

    private void awaitEvent() {
        await().atMost(5, SECONDS).pollInterval(FIVE_HUNDRED_MILLISECONDS).until(() -> !findAuditEvents().isEmpty());
    }
}
