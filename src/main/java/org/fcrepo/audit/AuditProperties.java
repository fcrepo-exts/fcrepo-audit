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

import static org.apache.jena.vocabulary.RDF.type;
import static org.fcrepo.audit.AuditNamespaces.REPOSITORY;
import static org.fcrepo.audit.AuditNamespaces.AUDIT;
import static org.fcrepo.audit.AuditNamespaces.PREMIS;
import static org.fcrepo.audit.AuditNamespaces.PROV;
import static org.fcrepo.audit.AuditNamespaces.EVENT_TYPE;
import static org.fcrepo.kernel.api.RdfLexicon.EVENT_NAMESPACE;

/**
 * @author acoburn
 * @since 2015-04-25
 */
public final class AuditProperties {

    public static final String INTERNAL_EVENT = AUDIT + "InternalEvent";
    public static final String PREMIS_EVENT = PREMIS + "Event";
    public static final String PROV_EVENT = PROV + "InstantaneousEvent";

    public static final String CONTENT_MOD = AUDIT + "contentModification";
    public static final String CONTENT_REM = AUDIT + "contentRemoval";
    public static final String METADATA_MOD = AUDIT + "metadataModification";

    public static final String CONTENT_ADD = EVENT_TYPE + "ing";
    public static final String OBJECT_ADD = EVENT_TYPE + "cre";
    public static final String OBJECT_REM = EVENT_TYPE + "del";

    public static final String PREMIS_TIME = PREMIS + "hasEventDateTime";
    public static final String PREMIS_AGENT = PREMIS + "hasEventRelatedAgent";
    public static final String PREMIS_TYPE = PREMIS + "hasEventType";

    public static final String RDF_TYPE = type.getURI();

    public static final String BINARY_TYPE = REPOSITORY + "Binary";
    public static final String RESOURCE_CREATION = EVENT_NAMESPACE + "ResourceCreation";
    public static final String RESOURCE_DELETION = EVENT_NAMESPACE + "ResourceDeletion";
    public static final String RESOURCE_MODIFICATION = EVENT_NAMESPACE + "ResourceModification";

    private AuditProperties() {
        // prevent instantiation
    }
}
