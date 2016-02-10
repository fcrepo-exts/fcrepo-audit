/*
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

import static com.hp.hpl.jena.datatypes.xsd.XSDDatatype.XSDdateTime;
import static com.hp.hpl.jena.datatypes.xsd.XSDDatatype.XSDstring;
import static com.hp.hpl.jena.rdf.model.ModelFactory.createDefaultModel;
import static com.hp.hpl.jena.rdf.model.ResourceFactory.createProperty;
import static com.hp.hpl.jena.rdf.model.ResourceFactory.createResource;
import static com.hp.hpl.jena.rdf.model.ResourceFactory.createTypedLiteral;

import static org.modeshape.jcr.api.JcrConstants.JCR_CONTENT;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import org.fcrepo.kernel.api.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.api.observer.FedoraEvent;
import org.fcrepo.kernel.api.services.ContainerService;
import org.fcrepo.kernel.api.utils.iterators.RdfStream;
import org.fcrepo.kernel.modeshape.rdf.impl.PrefixingIdentifierTranslator;

import org.modeshape.jcr.api.JcrTools;
import org.modeshape.jcr.api.Repository;
import org.modeshape.jcr.api.Session;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
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

    private Session session;
    private static JcrTools jcrTools = new JcrTools(true);

    /**
     * Register with the EventBus to receive events.
     * @throws RepositoryRuntimeException
     */
    @PostConstruct
    public void register() throws RepositoryRuntimeException {
        try {
            AUDIT_CONTAINER_LOCATION = System.getProperty(AUDIT_CONTAINER);
            if (AUDIT_CONTAINER_LOCATION != null) {
                LOGGER.info("Initializing: {}", this.getClass().getCanonicalName());
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
     * @throws RepositoryRuntimeException on error
     */
    @Subscribe
    public void recordEvent(final FedoraEvent event) throws RepositoryRuntimeException {
        LOGGER.debug("Event detected: {} {}", event.getUserID(), event.getPath());
        boolean isParentNodeLastModifiedEvent = false;
        final String eventType = AuditUtils.getEventURIs(event.getTypes());
        final Set<String> properties = event.getProperties();
        if (eventType.contains(AuditProperties.PROPERTY_CHANGED)) {
            isParentNodeLastModifiedEvent = true;
            final Iterator<String> propertiesIter = properties.iterator();
            String property;
            while (properties.iterator().hasNext()) {
                property = propertiesIter.next();
                if (!property.equals(AuditProperties.LAST_MODIFIED) &&
                        !property.equals(AuditProperties.LAST_MODIFIED_BY)) {
                    /* adding/removing a file updates the lastModified property of the parent container,
                    so ignore updates when only lastModified is changed */
                    isParentNodeLastModifiedEvent = false;
                    break;
                }
            }
        }
        if (!event.getPath().startsWith(AUDIT_CONTAINER_LOCATION)
                && !isParentNodeLastModifiedEvent) {
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
     * @throws RepositoryRuntimeException on error
     * @throws java.io.IOException on json mapping error
     */
    public void createAuditNode(final FedoraEvent event) throws RepositoryRuntimeException, IOException {
        try {
            final String userData = event.getUserData();
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode json = mapper.readTree(userData);
            final String userAgent = json.get("userAgent").asText();
            String baseURL = json.get("baseURL").asText();
            if (baseURL.endsWith("/")) {
                baseURL = baseURL.substring(0, baseURL.length() - 1);
            }
            String path = event.getPath();
            if ( path.endsWith("/" + JCR_CONTENT) ) {
                path = path.replaceAll("/" + JCR_CONTENT,"");
            }
            final String uri = baseURL + path;
            final Long timestamp =  event.getDate();
            final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            final String eventDate = df.format(new Date(timestamp));
            final String userID = event.getUserID();
            final String eventType = AuditUtils.getEventURIs(event.getTypes());
            final String properties = Joiner.on(',').join(event.getProperties());
            final String auditEventType = AuditUtils.getAuditEventType(eventType, properties);
            final FedoraResource auditResource = containerService.findOrCreate(session,
                    AUDIT_CONTAINER_LOCATION + "/" + event.getEventID());

            LOGGER.debug("Audit node {} created for event.", event.getEventID());

            final Model m = createDefaultModel();
            final String auditResourceURI = baseURL + AUDIT_CONTAINER_LOCATION + "/" + event.getEventID();
            final Resource s = createResource(auditResourceURI);
            m.add(createStatement(s, AuditProperties.RDF_TYPE, createResource(AuditProperties.INTERNAL_EVENT)));
            m.add(createStatement(s, AuditProperties.RDF_TYPE, createResource(AuditProperties.PREMIS_EVENT)));
            m.add(createStatement(s, AuditProperties.RDF_TYPE, createResource(AuditProperties.PROV_EVENT)));
            m.add(createStatement(s, AuditProperties.PREMIS_TIME, createTypedLiteral(eventDate, XSDdateTime)));
            m.add(createStatement(s, AuditProperties.PREMIS_AGENT, createTypedLiteral(userID, XSDstring)));
            m.add(createStatement(s, AuditProperties.PREMIS_AGENT, createTypedLiteral(userAgent, XSDstring)));
            if (auditEventType != null) {
                m.add(createStatement(s, AuditProperties.PREMIS_TYPE, createResource(auditEventType)));
            }

            auditResource.replaceProperties(new PrefixingIdentifierTranslator(session, baseURL + "/"), m,
                    new RdfStream());

            // set link to impacted object using a URI property to preserve the link if it's deleted
            try {
                auditResource.getNode().setProperty(PREMIS_OBJ, new URI(uri).toString(), PropertyType.URI);
            } catch (URISyntaxException e) {
                LOGGER.warn("Error creating URI for repository resource {}", uri);
            }

            session.save();
        } catch (RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    @VisibleForTesting
    protected Statement createStatement(final Resource subject, final String property, final RDFNode object) {
        return ResourceFactory.createStatement(subject, createProperty(property), object);
    }
}
