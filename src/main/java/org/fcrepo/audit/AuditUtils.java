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

import static org.fcrepo.audit.AuditProperties.BINARY_TYPE;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Set;
import java.util.stream.Collectors;

import org.fcrepo.kernel.api.observer.EventType;

import org.slf4j.Logger;

/**
 * Audit utility functions
 * @author acoburn
 * @since 2015-04-25
 */
public class AuditUtils {

    private static final Logger LOGGER = getLogger(AuditUtils.class);

    /**
     * Returns the set of event type URIs for the integer event types.
     *
     * @param types to be converted to URIs
     * @return set of type URIs
     */
    public static Set<String> getEventURIs(final Set<EventType> types) {
        final Set<String> uris = types.stream().map(EventType::getType).collect(Collectors.toSet());
        LOGGER.debug("Constructed event type URIs: {}", uris);
        return uris;
    }

    /**
     * Returns the Audit event type based on fedora event type and properties.
     *
     * @param eventTypes from Fedora
     * @param resourceTypes associated with the object of the Fedora event
     * @return Audit event
     */
    public static String getAuditEventType(final Set<String> eventTypes, final Set<String> resourceTypes) {
        // mapping event type / resource types to audit event type
        if (eventTypes.contains(AuditProperties.RESOURCE_CREATION)) {
            if (resourceTypes != null && resourceTypes.contains(BINARY_TYPE)) {
                return AuditProperties.CONTENT_ADD;
            } else {
                return AuditProperties.OBJECT_ADD;
            }
        } else if (eventTypes.contains(AuditProperties.RESOURCE_DELETION)) {
            if (resourceTypes != null && resourceTypes.contains(BINARY_TYPE)) {
                return AuditProperties.CONTENT_REM;
            } else {
                return AuditProperties.OBJECT_REM;
            }
        } else if (eventTypes.contains(AuditProperties.RESOURCE_MODIFICATION)) {
            if (resourceTypes != null && resourceTypes.contains(BINARY_TYPE)) {
                return AuditProperties.CONTENT_MOD;
            } else {
                return AuditProperties.METADATA_MOD;
            }
        }
        return null;
    }

    private AuditUtils() {
        // prevent instantiation
    }
}
