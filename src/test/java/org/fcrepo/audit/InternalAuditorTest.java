/**
 * Copyright 2015 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import static org.fcrepo.audit.AuditNamespaces.AUDIT;
import static org.fcrepo.audit.AuditNamespaces.EVENT_TYPE;
import static org.fcrepo.audit.AuditNamespaces.PROV;
import static org.fcrepo.audit.AuditNamespaces.REPOSITORY;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.security.auth.login.LoginException;

import org.fcrepo.kernel.models.Container;
import org.fcrepo.kernel.observer.FedoraEvent;
import org.fcrepo.kernel.services.ContainerService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.modeshape.jcr.api.Repository;
import org.modeshape.jcr.api.Session;

import com.google.common.eventbus.EventBus;

/**
 * @author mohideen
 * @date 4/16/15.
 */
public class InternalAuditorTest {

    private InternalAuditor testTnternalAuditor;

    private static final String AUDIT_CONTAINER = "fcrepo.audit.container";

    private static final String identifier = "27c605e4-98c6-4240-86be-f1bb1971d694";

    private static final long timestamp = 1428676236521L;

    private static final String userID = "bypassAdmin";

    private static final String userAgent = "{\"baseURL\":\"http://localhost:8080/rest\"," +
            "\"userAgent\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_5) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.76 Safari/537.36\"}";

    private static final String pid = "0f/69/a3/fe/0f69a3fe-2526-4d2a-a842-cf475a3b9858";

    @Mock
    private EventBus mockBus;

    @Mock
    private Repository mockRepository;

    @Mock
    private ContainerService mockContainerService;

    @Mock
    private Session mockSession;

    @Mock
    private static Container mockContainer;

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
        testTnternalAuditor = new InternalAuditor();
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
        final Set<Integer> eventTypes = new HashSet<>(Arrays.asList(Event.NODE_ADDED, Event.PROPERTY_ADDED));
        final Set<String> eventProps = new HashSet<>(Arrays.asList(REPOSITORY + "lastModified", REPOSITORY +
                        "primaryType",
                REPOSITORY + "lastModifiedBy", REPOSITORY + "created", REPOSITORY + "mixinTypes",
                REPOSITORY + "createdBy", REPOSITORY + "uuid"));
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, eventProps);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(mockNode).addMixin("fedora:Resource");
        verify(mockNode).setProperty("rdf:type", AUDIT + "InternalEvent");
        verify(mockNode).setProperty("rdf:type", "premis:Event");
        verify(mockNode).setProperty("rdf:type", PROV + "InstantaneousEvent");
        verify(mockNode).setProperty("premis:hasEventDateTime", "2015-04-10T14:30:36Z");
        verify(mockNode).setProperty("premis:hasEventRelatedObject",
                "http://localhost:8080/rest/non/audit/container/path");
        verify(mockNode).setProperty("premis:hasEventRelatedAgent", userID);
        verify(mockNode).setProperty("premis:hasEventRelatedAgent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_5) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.76 Safari/537.36");
        verify(mockNode).setProperty("premis:hasEventType", OBJECT_ADD);
    }

    @Test
    public void testNodeRemoved() throws Exception {
        final Set<Integer> eventTypes = new HashSet<>(Arrays.asList(Event.NODE_REMOVED));
        final Set<String> eventProps = new HashSet<>();
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, eventProps);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(mockNode).setProperty("premis:hasEventType", OBJECT_REM);
    }

    @Test
    public void testPropertiesChanged() throws Exception {
        final Set<Integer> eventTypes = new HashSet<>(Arrays.asList(Event.PROPERTY_CHANGED, Event.PROPERTY_ADDED));
        final Set<String> eventProps = new HashSet<>(Arrays.asList(REPOSITORY + "lastModified",
                "http://purl.org/dc/elements/1.1/title"));
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, eventProps);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(mockNode).setProperty("premis:hasEventType", METADATA_MOD);
    }

    @Test
    public void testFileAdded() throws Exception {
        final Set<Integer> eventTypes = new HashSet<>(Arrays.asList(Event.NODE_ADDED, Event.PROPERTY_ADDED));
        final Set<String> eventProps = new HashSet<>(Arrays.asList(REPOSITORY + "lastModified",
                REPOSITORY + "primaryType", REPOSITORY + "lastModifiedBy", REPOSITORY + "created",
                REPOSITORY + "mixinTypes", REPOSITORY + "createdBy", REPOSITORY + "uuid", REPOSITORY + "hasContent",
                "premis:hasSize", "premis:hasOriginalName", REPOSITORY + "digest"));
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, eventProps);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(mockNode).setProperty("premis:hasEventType", CONTENT_ADD);
    }

    @Test
    public void testFileChanged() throws Exception {
        final Set<Integer> eventTypes = new HashSet<>(Arrays.asList(Event.PROPERTY_CHANGED));
        final Set<String> eventProps = new HashSet<>(Arrays.asList(REPOSITORY + "lastModified",
                REPOSITORY + "hasContent", "premis:hasSize", "premis:hasOriginalName", REPOSITORY + "digest"));
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, eventProps);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(mockNode).setProperty("premis:hasEventType", CONTENT_MOD);
    }

    @Test
    public void testFileRemoved() throws Exception {
        final Set<Integer> eventTypes = new HashSet<>(Arrays.asList(Event.NODE_REMOVED));
        final Set<String> eventProps = new HashSet<>(Arrays.asList( REPOSITORY + "hasContent"));
        final FedoraEvent mockFedoraEvent = setupMockEvent(eventTypes, eventProps);
        when(mockContainerService.findOrCreate(any(Session.class), anyString())).thenReturn(mockContainer);
        when(mockContainer.getNode()).thenReturn(mockNode);
        testTnternalAuditor.recordEvent(mockFedoraEvent);
        verify(mockNode).setProperty("premis:hasEventType", CONTENT_REM);
    }

    private static FedoraEvent setupMockEvent(final Set<Integer> eventTypes,
                                           final Set<String> eventProperties) throws RepositoryException {
        final FedoraEvent mockFedoraEvent = mock(FedoraEvent.class);
        when(mockFedoraEvent.getDate()).thenReturn(timestamp);
        when(mockFedoraEvent.getUserID()).thenReturn(userID);
        when(mockFedoraEvent.getUserData()).thenReturn(userAgent);
        when(mockFedoraEvent.getPath()).thenReturn("/non/audit/container/path");
        when(mockFedoraEvent.getTypes()).thenReturn(eventTypes);
        when(mockFedoraEvent.getProperties()).thenReturn(eventProperties);
        return mockFedoraEvent;
    }
}
