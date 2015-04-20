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
import static org.fcrepo.kernel.RdfLexicon.REPOSITORY_NAMESPACE;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.fcrepo.kernel.identifiers.PidMinter;
import org.fcrepo.kernel.observer.FedoraEvent;
import org.fcrepo.kernel.services.ContainerService;
import org.fcrepo.kernel.utils.EventType;
import org.fcrepo.mint.UUIDPathMinter;

import org.modeshape.jcr.api.Repository;
import org.modeshape.jcr.api.Session;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

/**
 * @author mohideen
 * @date 4/15/15.
 */
public class InternalAuditor implements Auditor {

    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = getLogger(InternalAuditor.class);

    private static final String AUDIT_CONTAINER = "fcrepo.audit.container";

    private static String AUDIT_CONTAINER_LOCATION;

    @Inject
    private EventBus eventBus;

    @Inject
    private Repository repository;

    @Inject
    private ContainerService containerService;

    private Session session;

    private static final PidMinter pidMinter =  new UUIDPathMinter();

    /**
     * Register with the EventBus to receive events.
     */
    @PostConstruct
    public void register() throws RepositoryException {
        AUDIT_CONTAINER_LOCATION = System.getProperty(AUDIT_CONTAINER);
        if (AUDIT_CONTAINER_LOCATION != null) {
            LOGGER.info("Initializing: {}", this.getClass().getCanonicalName());
            eventBus.register(this);
            if (!AUDIT_CONTAINER_LOCATION.startsWith("/")) {
                AUDIT_CONTAINER_LOCATION = "/" + AUDIT_CONTAINER_LOCATION;
            }
            session = repository.login();
            containerService.findOrCreate(session, AUDIT_CONTAINER_LOCATION);
            session.save();
        } else {
            LOGGER.warn("Cannot Initialize: {}", this.getClass().getCanonicalName());
            LOGGER.warn("System property not found: " + AUDIT_CONTAINER);
        }
    }

    /**
     * Fedora internal events are received and processed by this method.
     *
     * @param event
     *        The {@link FedoraEvent} to record.
     * @throws RepositoryException
     */
    @Subscribe
    public void recordEvent(final FedoraEvent event) throws RepositoryException, IOException {
        LOGGER.debug("Event detected: {} {}", event.getUserID(), event.getPath());
        boolean isParentNodeLastModifiedEvent = false;
        final String eventType = getEventURIs(event.getTypes());
        final String properties = Joiner.on(',').join(event.getProperties());
        if (eventType.contains(PROPERTY_CHANGED)) {
            if (properties != null) {
                if (properties.equals(LAST_MODIFIED)
                        || properties.equals(LAST_MODIFIED_BY)
                        || properties.equals(LAST_MODIFIED + "," + LAST_MODIFIED_BY)
                        || properties.equals(LAST_MODIFIED_BY + "," + LAST_MODIFIED)) {

                /* adding/removing a file updates the lastModified property of the parent container,
                so ignore updates when only lastModified is changed */
                    isParentNodeLastModifiedEvent = true;
                }
            }
        }
        if (!event.getPath().startsWith(AUDIT_CONTAINER_LOCATION)
                && !isParentNodeLastModifiedEvent) {
            createAuditNode(event);
        }
    }

    /**
     * Close external connections
     */
    @PreDestroy
    public void releaseConnections() {
        LOGGER.debug("Tearing down: {}", this.getClass().getCanonicalName());
        eventBus.unregister(this);
    }

    // namespaces and properties
    private static final String INTERNAL_EVENT = AUDIT + "InternalEvent";
    private static final String PREMIS_EVENT = "premis:Event";
    private static final String PROV_EVENT = PROV + "InstantaneousEvent";

    private static final String CONTENT_MOD = AUDIT + "contentModification";
    private static final String CONTENT_REM = AUDIT + "contentRemoval";
    private static final String METADATA_MOD = AUDIT + "metadataModification";

    private static final String CONTENT_ADD = EVENT_TYPE + "ing";
    private static final String OBJECT_ADD = EVENT_TYPE + "cre";
    private static final String OBJECT_REM = EVENT_TYPE + "del";

    private static final String PREMIS_TIME = "premis:hasEventDateTime";
    private static final String PREMIS_OBJ = "premis:hasEventRelatedObject";
    private static final String PREMIS_AGENT = "premis:hasEventRelatedAgent";
    private static final String PREMIS_TYPE = "premis:hasEventType";

    private static final String RDF_TYPE = "rdf:type";

    private static final String HAS_CONTENT = REPOSITORY + "hasContent";
    private static final String LAST_MODIFIED = REPOSITORY + "lastModified";
    private static final String LAST_MODIFIED_BY = REPOSITORY + "lastModifiedBy";
    private static final String NODE_ADDED = REPOSITORY + "NODE_ADDED";
    private static final String NODE_REMOVED = REPOSITORY + "NODE_REMOVED";
    private static final String PROPERTY_CHANGED = REPOSITORY + "PROPERTY_CHANGED";

    /**
     * Creates a node for the audit event under the configured container.
     *
     * @param event
     * @throws RepositoryException
     */
    public void createAuditNode(final FedoraEvent event) throws RepositoryException, IOException {
        final String uuid = pidMinter.mintPid();
        final String baseURL = event.getIdentifier();
        final String identifier = event.getIdentifier();
        final String userData = event.getUserData();
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode json = mapper.readTree(userData);
        final String userAgent = json.get("userAgent").asText();
        final Long timestamp =  event.getDate();
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String eventDate = df.format(new Date(timestamp));
        final String userID = event.getUserID();
        final String eventType = getEventURIs(event.getTypes());
        final String properties = Joiner.on(',').join(event.getProperties());
        final Node auditNode = containerService.findOrCreate(session, "/audit/" + uuid).getNode();

        LOGGER.debug("Audit node {} created for event.", uuid);

        auditNode.addMixin("fedora:Resource");
        auditNode.setProperty(RDF_TYPE, INTERNAL_EVENT);
        auditNode.setProperty(RDF_TYPE, PREMIS_EVENT);
        auditNode.setProperty(RDF_TYPE, PROV_EVENT);
        auditNode.setProperty(PREMIS_TIME, eventDate);
        auditNode.setProperty(PREMIS_OBJ, identifier);
        auditNode.setProperty(PREMIS_AGENT, userID);
        auditNode.setProperty(PREMIS_AGENT, userAgent);

        // mapping event type/properties to audit event type
        if (eventType.contains(NODE_ADDED)) {
            if (properties != null && properties.contains(HAS_CONTENT)) {
                auditNode.setProperty(PREMIS_TYPE, CONTENT_ADD);
            } else {
                auditNode.setProperty(PREMIS_TYPE, OBJECT_ADD);
            }
        } else if (eventType.contains(NODE_REMOVED)) {
            if (properties != null && properties.contains(HAS_CONTENT)) {
                auditNode.setProperty(PREMIS_TYPE, CONTENT_REM);
            } else {
                auditNode.setProperty(PREMIS_TYPE, OBJECT_REM);
            }
        } else if (eventType.contains(PROPERTY_CHANGED)) {
            if (properties != null && properties.contains(HAS_CONTENT)) {
                auditNode.setProperty(PREMIS_TYPE, CONTENT_MOD);
            } else {
                auditNode.setProperty(PREMIS_TYPE, METADATA_MOD);
            }
        }

        session.save();

    }


    private static String getEventURIs(final Set<Integer> types) {
        final String uris = Joiner.on(',').join(Iterables.transform(types, new Function<Integer, String>() {

            @Override
            public String apply(final Integer type) {
                return REPOSITORY_NAMESPACE + EventType.valueOf(type);
            }
        }));
        LOGGER.debug("Constructed event type URIs: {}", uris);
        return uris;
    }
}
