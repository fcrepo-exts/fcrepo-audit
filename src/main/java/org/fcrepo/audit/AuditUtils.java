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

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Set;

import org.fcrepo.kernel.api.utils.EventType;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import org.slf4j.Logger;

/**
 * Audit utility functions
 * @author acoburn
 * @since 2015-04-25
 */
public class AuditUtils {

    private static final Logger LOGGER = getLogger(AuditUtils.class);

    /**
     * Returns the comma event types string for the integer event types.
     *
     * @param types to be converted to URIs
     * @return comma-delimited string of type URIs
     */
    public static String getEventURIs(final Set<Integer> types) {
        final String uris = Joiner.on(',').join(Iterables.transform(types, new Function<Integer, String>() {

            @Override
            public String apply(final Integer type) {
                return AuditNamespaces.REPOSITORY + EventType.valueOf(type);
            }
        }));
        LOGGER.debug("Constructed event type URIs: {}", uris);
        return uris;
    }

    /**
     * Returns the Audit event type based on fedora event type and properties.
     *
     * @param eventType from Fedora
     * @param properties associated with the Fedora event
     * @return Audit event
     */
    public static String getAuditEventType(final String eventType, final String properties) {
        // mapping event type/properties to audit event type
        if (eventType.contains(AuditProperties.NODE_ADDED)) {
            if (properties != null && properties.contains(AuditProperties.HAS_CONTENT)) {
                return AuditProperties.CONTENT_ADD;
            } else {
                return AuditProperties.OBJECT_ADD;
            }
        } else if (eventType.contains(AuditProperties.NODE_REMOVED)) {
            if (properties != null && properties.contains(AuditProperties.HAS_CONTENT)) {
                return AuditProperties.CONTENT_REM;
            } else {
                return AuditProperties.OBJECT_REM;
            }
        } else if (eventType.contains(AuditProperties.PROPERTY_CHANGED)) {
            if (properties != null && properties.contains(AuditProperties.HAS_CONTENT)) {
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
