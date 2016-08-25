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

import static org.apache.jena.datatypes.xsd.XSDDatatype.XSDdateTime;
import static org.apache.jena.datatypes.xsd.XSDDatatype.XSDstring;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral;
import static java.util.EnumSet.noneOf;
import static org.fcrepo.kernel.api.observer.OptionalValues.BASE_URL;
import static org.fcrepo.kernel.api.observer.OptionalValues.USER_AGENT;
import static org.fcrepo.kernel.modeshape.utils.FedoraTypesUtils.getJcrNode;
import static org.slf4j.LoggerFactory.getLogger;

import static org.fcrepo.audit.AuditProperties.INTERNAL_EVENT;
import static org.fcrepo.audit.AuditProperties.PREMIS_AGENT;
import static org.fcrepo.audit.AuditProperties.PREMIS_EVENT;
import static org.fcrepo.audit.AuditProperties.PREMIS_TIME;
import static org.fcrepo.audit.AuditProperties.PREMIS_TYPE;
import static org.fcrepo.audit.AuditProperties.PROV_EVENT;
import static org.fcrepo.audit.AuditProperties.RDF_TYPE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import org.fcrepo.kernel.api.RequiredRdfContext;
import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.identifiers.IdentifierConverter;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.api.observer.FedoraEvent;
import org.fcrepo.kernel.api.services.ContainerService;
import org.fcrepo.kernel.modeshape.rdf.impl.PrefixingIdentifierTranslator;

import org.modeshape.jcr.api.JcrTools;
import org.modeshape.jcr.api.Repository;
import org.modeshape.jcr.api.Session;
import org.slf4j.Logger;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

/**
 * Auditor implementation that creates audit nodes in the repository.
 * @author mohideen
 * @author escowles
 * @since 2015-04-15
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

    private static final UuidPathMinter pathMinter = new UuidPathMinter();

    private Session session;
    private static JcrTools jcrTools = new JcrTools(true);

    /**
     * Register with the EventBus to receive events.
     */
    @PostConstruct
    public void register() {
        try {
            AUDIT_CONTAINER_LOCATION = System.getProperty(AUDIT_CONTAINER);
            if (AUDIT_CONTAINER_LOCATION != null) {
                LOGGER.info("Initializing: {}, {}", this.getClass().getCanonicalName(), AUDIT_CONTAINER_LOCATION);
                eventBus.register(this);
                if (!AUDIT_CONTAINER_LOCATION.startsWith("/")) {
                    AUDIT_CONTAINER_LOCATION = "/" + AUDIT_CONTAINER_LOCATION;
                }
                if (AUDIT_CONTAINER_LOCATION.endsWith("/")) {
                    AUDIT_CONTAINER_LOCATION = AUDIT_CONTAINER_LOCATION.substring(0,
                            AUDIT_CONTAINER_LOCATION.length() - 2);
                }
                session = repository.login();
                containerService.findOrCreate(session, AUDIT_CONTAINER_LOCATION);

                LOGGER.debug("Registering audit CND");
                jcrTools.registerNodeTypes(session, "audit.cnd");

                session.save();
            } else {
                LOGGER.warn("Cannot Initialize: {}", this.getClass().getCanonicalName());
                LOGGER.warn("System property not found: " + AUDIT_CONTAINER);
            }
        } catch (RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Fedora internal events are received and processed by this method.
     *
     * @param event
     *        The {@link FedoraEvent} to record.
     */
    @Subscribe
    public void recordEvent(final FedoraEvent event) {
        LOGGER.debug("Event detected: {} {}", event.getUserID(), event.getPath());
        if (!event.getPath().startsWith(AUDIT_CONTAINER_LOCATION) && !event.getPath().isEmpty()) {
            try {
                createAuditNode(event);
            } catch (IOException e) {
                throw new RepositoryRuntimeException(e);
            }
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

    // JCR property name, not URI
    private static final String PREMIS_OBJ = "premis:hasEventRelatedObject";

    /**
     * Creates a node for the audit event under the configured container.
     *
     * @param event to be persisted in the repository
     * @throws java.io.IOException on json mapping error
     */
    public void createAuditNode(final FedoraEvent event) throws IOException {
        try {
            final String userAgent = event.getInfo().get(USER_AGENT);
            final String baseURL = event.getInfo().get(BASE_URL);
            final String path = event.getPath();
            final String uri = baseURL + path;
            final Instant timestamp =  event.getDate();
            final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            final String eventDate = df.format(timestamp.toEpochMilli());
            final String userID = event.getUserID();
            final Set<String> eventTypes = AuditUtils.getEventURIs(event.getTypes());
            final Set<String> resourceTypes = event.getResourceTypes();
            final String auditEventType = AuditUtils.getAuditEventType(eventTypes, resourceTypes);

            final String eventPath = getEventPath(event.getEventID());
            final FedoraResource auditResource = containerService.findOrCreate(session,
                    AUDIT_CONTAINER_LOCATION + "/" + eventPath);

            LOGGER.debug("Audit node {} created for event.", event.getEventID());

            final Model m = createDefaultModel();
            final String auditResourceURI = baseURL + AUDIT_CONTAINER_LOCATION + "/" + eventPath;
            final Resource s = createResource(auditResourceURI);
            m.add(createStatement(s, RDF_TYPE, createResource(INTERNAL_EVENT)));
            m.add(createStatement(s, RDF_TYPE, createResource(PREMIS_EVENT)));
            m.add(createStatement(s, RDF_TYPE, createResource(PROV_EVENT)));
            m.add(createStatement(s, PREMIS_TIME, createTypedLiteral(eventDate, XSDdateTime)));
            m.add(createStatement(s, PREMIS_AGENT, createTypedLiteral(userID, XSDstring)));
            m.add(createStatement(s, PREMIS_AGENT, createTypedLiteral(userAgent, XSDstring)));
            if (auditEventType != null) {
                m.add(createStatement(s, PREMIS_TYPE, createResource(auditEventType)));
            }

            final IdentifierConverter<Resource, FedoraResource> translator =
                new PrefixingIdentifierTranslator(session, baseURL + "/");
            auditResource.replaceProperties(translator, m,
                    auditResource.getTriples(translator, noneOf(RequiredRdfContext.class)));

            // set link to impacted object using a URI property to preserve the link if it's deleted
            try {
                getJcrNode(auditResource).setProperty(PREMIS_OBJ, new URI(uri).toString(), PropertyType.URI);
            } catch (URISyntaxException e) {
                LOGGER.warn("Error creating URI for repository resource {}", uri);
            }

            session.save();
        } catch (RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    @VisibleForTesting
    protected String getEventPath(final String eventID) {
        if (!eventID.startsWith("urn:uuid:")) {
            throw new IllegalArgumentException("Event ID must be a 'urn:uuid:'" + eventID);
        }
        return pathMinter.get(eventID.substring("urn:uuid:".length()));
    }

    @VisibleForTesting
    protected Statement createStatement(final Resource subject, final String property, final RDFNode object) {
        return ResourceFactory.createStatement(subject, createProperty(property), object);
    }

}
