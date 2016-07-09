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
package org.fcrepo.audit;

import static com.hp.hpl.jena.datatypes.xsd.XSDDatatype.XSDdateTime;
import static com.hp.hpl.jena.datatypes.xsd.XSDDatatype.XSDstring;
import static com.hp.hpl.jena.rdf.model.ResourceFactory.createResource;
import static com.hp.hpl.jena.rdf.model.ResourceFactory.createTypedLiteral;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.fcrepo.audit.AuditNamespaces.AUDIT;
import static org.fcrepo.audit.AuditNamespaces.EVENT_TYPE;
import static org.fcrepo.audit.AuditNamespaces.PREMIS;
import static org.fcrepo.audit.AuditNamespaces.PROV;
import static org.fcrepo.audit.AuditNamespaces.REPOSITORY;
import static org.fcrepo.kernel.api.RdfLexicon.RDF_NAMESPACE;
import static org.fcrepo.kernel.api.observer.EventType.RESOURCE_CREATION;
import static org.fcrepo.kernel.api.observer.EventType.RESOURCE_DELETION;
import static org.fcrepo.kernel.api.observer.EventType.RESOURCE_MODIFICATION;
import static org.fcrepo.kernel.api.observer.OptionalValues.BASE_URL;
import static org.fcrepo.kernel.api.observer.OptionalValues.USER_AGENT;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.internal.util.collections.Sets.newSet;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.security.auth.login.LoginException;

import org.fcrepo.kernel.api.observer.FedoraEvent;
import org.fcrepo.kernel.api.services.ContainerService;
import org.fcrepo.kernel.modeshape.ContainerImpl;

import org.fcrepo.kernel.api.observer.EventType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.modeshape.jcr.api.JcrTools;
import org.modeshape.jcr.api.Repository;
import org.modeshape.jcr.api.Session;

import com.google.common.eventbus.EventBus;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * @author mohideen
 * @since 4/16/15.
 */
public class InternalAuditorTest {

    private InternalAuditor testTnternalAuditor;

    private static final String AUDIT_CONTAINER = "fcrepo.audit.container";

    private static final String identifier = "27c605e4-98c6-4240-86be-f1bb1971d694";

    private static final String identifierPath = "27/c6/05/e4/" + identifier;

    private static final Instant timestamp = Instant.ofEpochMilli(1428676236521L);

    private static final String userID = "bypassAdmin";

    private static final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_5) AppleWebKit/537.36";

    private static final Map<String, String> auxInfo = new HashMap<>();

    private String baseUrl = "http://localhost:8080/rest";

    @Mock
    private EventBus mockBus;

    @Mock
    private Repository mockRepository;

    @Mock
    private ContainerService mockContainerService;

    @Mock
    private Session mockSession;

    @Mock
    private static ContainerImpl mockContainer;

    @Mock
    private static Node mockNode;

    private static final String CONTENT_MOD = AUDIT + "contentModification";
    private static final String CONTENT_REM = AUDIT + "contentRemoval";
    private static final String METADATA_MOD = AUDIT + "metadataModification";

    private static final String CONTENT_ADD = EVENT_TYPE + "ing";
    private static final String OBJECT_ADD = EVENT_TYPE + "cre";
    private static final String OBJECT_REM = EVENT_TYPE + "del";

    @Before
    public void setUp() {
        testTnternalAuditor = spy(new InternalAuditor());
        auxInfo.put(BASE_URL, baseUrl);
        auxInfo.put(USER_AGENT, userAgent);
        initMocks(this);
        setField(testTnternalAuditor, "eventBus", mockBus);
        setField(testTnternalAuditor, "repository", mockRepository);
        setField(testTnternalAuditor, "session", mockSession);
        setField(testTnternalAuditor, "containerService", mockContainerService);
        setField(testTnternalAuditor, "AUDIT_CONTAINER_LOCATION", "/audit");
    }

    @Test
    public void testRegister() throws RepositoryException, LoginException {
        System.setProperty(AUDIT_CONTAINER, "/audit");
        when(mockRepository.login()).thenReturn(mockSession);
        setField(testTnternalAuditor, "jcrTools", mock(JcrTools.class));
        testTnternalAuditor.register();
        verify(mockContainerService).findOrCreate(mockSession, "/audit");
        verify(mockBus).register(any(InternalAuditor.class));
        System.clearProperty(AUDIT_CONTAINER);
    }

    @Test
    public void testRegisterUnsuccessful() throws RepositoryException, LoginException  {
        when(mockRepository.login()).thenReturn(mockSession);
        testTnternalAuditor.register();
        verify(mockContainerService, never()).findOrCreate(mockSession, eq(anyString()));
        verify(mockBus, never()).register(any(InternalAuditor.class));
    }

    @Test
    public void testUnregister() {
        testTnternalAuditor.releaseConnections();
        verify(mockBus).unregister(anyObject());
    }

    @Test
    public void testNodeAddedWithProperties() throws Exception {
        final Set<EventType> eventTypes = newSet(RESOURCE_CREATION, RESOURCE_MODIFICATION);
        final Set<String> resourceTypes = newSet(REPOSITORY + "Resource", REPOSITORY + "Container");
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, resourceTypes);
        final String eventID = "urn:uuid:" + identifier;
        when(mockFedoraEvent.getEventID()).thenReturn(eventID);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(RDF_NAMESPACE + "type"),
                eq(createResource(AUDIT + "InternalEvent")));
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(RDF_NAMESPACE + "type"),
                eq(createResource(PREMIS + "Event")));
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(RDF_NAMESPACE + "type"),
                eq(createResource(PROV + "InstantaneousEvent")));
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(PREMIS + "hasEventDateTime"),
                eq(createTypedLiteral("2015-04-10T14:30:36Z", XSDdateTime)));
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(PREMIS + "hasEventRelatedAgent"),
                eq(createTypedLiteral(userID, XSDstring)));
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(PREMIS + "hasEventRelatedAgent"),
                eq(createTypedLiteral(userAgent, XSDstring)));
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(PREMIS + "hasEventType"),
                eq(createResource(OBJECT_ADD)));

        verify(mockNode).setProperty(eq("premis:hasEventRelatedObject"),
                eq("http://localhost:8080/rest/non/audit/container/path"), eq(PropertyType.URI));
        verify(mockContainerService).findOrCreate( any(Session.class), eq("/audit/" + identifierPath));
    }

    @Test
    public void testNodeRemoved() throws Exception {
        final Set<EventType> eventTypes = singleton(RESOURCE_DELETION);
        final Set<String> resourceTypes = emptySet();
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, resourceTypes);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(PREMIS + "hasEventType"),
                eq(createResource(OBJECT_REM)));
    }

    @Test
    public void testPropertiesChanged() throws Exception {
        final Set<EventType> eventTypes = singleton(RESOURCE_MODIFICATION);
        final Set<String> resourceTypes = newSet(REPOSITORY + "Resource", REPOSITORY + "Container");
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, resourceTypes);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(PREMIS + "hasEventType"),
                eq(createResource(METADATA_MOD)));
    }

    @Test
    public void testFileAdded() throws Exception {
        final Set<EventType> eventTypes = newSet(RESOURCE_CREATION, RESOURCE_MODIFICATION);
        final Set<String> resourceTypes = newSet(REPOSITORY + "Resource", REPOSITORY + "Binary");
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, resourceTypes);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(PREMIS + "hasEventType"),
                eq(createResource(CONTENT_ADD)));
    }

    @Test
    public void testFileChanged() throws Exception {
        final Set<EventType> eventTypes = singleton(RESOURCE_MODIFICATION);
        final Set<String> resourceTypes = newSet(REPOSITORY + "Resource", REPOSITORY + "Binary");
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, resourceTypes);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(PREMIS + "hasEventType"),
                eq(createResource(CONTENT_MOD)));
    }

    @Test
    public void testFileRemoved() throws Exception {
        final Set<EventType> eventTypes = singleton(RESOURCE_DELETION);
        final Set<String> resourceTypes = newSet(REPOSITORY + "Resource", REPOSITORY + "Binary");
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, resourceTypes);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(testTnternalAuditor).createStatement(any(Resource.class), eq(PREMIS + "hasEventType"),
                eq(createResource(CONTENT_REM)));
    }

    @Test
    public void testGetAuditEventTypeCreation() throws Exception {
        final Set<String> eventTypes = newSet(RESOURCE_CREATION.getType(), RESOURCE_MODIFICATION.getType());
        final Set<String> resourceTypes = newSet(REPOSITORY + "Resource", REPOSITORY + "Container");
        assertEquals(AuditUtils.getAuditEventType(eventTypes, resourceTypes), OBJECT_ADD);
    }

    @Test
    public void testGetAuditEventTypeModification() throws Exception {
        final Set<String> eventTypes = newSet(RESOURCE_MODIFICATION.getType());
        final Set<String> resourceTypes = newSet(REPOSITORY + "Resource", REPOSITORY + "Container");
        assertEquals(AuditUtils.getAuditEventType(eventTypes, resourceTypes), METADATA_MOD);
    }

    @Test
    public void testGetAuditEventTypeRemoval() throws Exception {
        final Set<String> eventTypes = newSet(RESOURCE_DELETION.getType());
        final Set<String> resourceTypes = emptySet();
        assertEquals(AuditUtils.getAuditEventType(eventTypes, resourceTypes), OBJECT_REM);
    }

    @Test (expected = IllegalArgumentException.class)
    public void testGetEventPathInvalid() {
        testTnternalAuditor.getEventPath(identifier);
    }


    @Test
    public void testGetEventPath() {
        final String path = testTnternalAuditor.getEventPath("urn:uuid:" + identifier);
        assertEquals(identifierPath, path);
    }


    private static FedoraEvent setupMockEvent(final Set<EventType> eventTypes,
                                           final Set<String> resourceTypes) throws RepositoryException {
        final FedoraEvent mockFedoraEvent = mock(FedoraEvent.class);
        when(mockFedoraEvent.getDate()).thenReturn(timestamp);
        when(mockFedoraEvent.getUserID()).thenReturn(userID);
        when(mockFedoraEvent.getInfo()).thenReturn(auxInfo);
        when(mockFedoraEvent.getPath()).thenReturn("/non/audit/container/path");
        when(mockFedoraEvent.getEventID()).thenReturn("urn:uuid:" + identifier);
        when(mockFedoraEvent.getTypes()).thenReturn(eventTypes);
        when(mockFedoraEvent.getResourceTypes()).thenReturn(resourceTypes);
        return mockFedoraEvent;
    }
}
