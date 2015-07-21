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

import static org.slf4j.LoggerFactory.getLogger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.fcrepo.kernel.api.observer.FedoraEvent;

import org.slf4j.Logger;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

/**
 * A proof-of-concept Auditor implementation that uses Logback.
 *
 * @author Edwin Shin
 * @since 2014
 */
public class LogbackAuditor implements Auditor {

    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = getLogger(LogbackAuditor.class);

    @Inject
    private EventBus eventBus;

    /**
     * Register with the EventBus to receive events.
     */
    @PostConstruct
    public void register() {
        LOGGER.debug("Initializing: {}", this.getClass().getCanonicalName());
        eventBus.register(this);
    }

    @Override
    @Subscribe
    public void recordEvent(final FedoraEvent e) {
        LOGGER.info("{} {}", e.getUserID(), e.getPath());
    }
}
