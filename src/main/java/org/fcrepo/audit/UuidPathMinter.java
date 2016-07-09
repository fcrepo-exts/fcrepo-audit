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

import java.util.StringJoiner;
import java.util.stream.IntStream;

/**
 * PID minter that creates hierarchical IDs from a given UUID.
 *
 * @author  awoods
 * @since 2016-07-03
 */
public class UuidPathMinter {

    private static final int DEFAULT_LENGTH = 2;
    private static final int DEFAULT_COUNT = 4;

    /**
     * Mint a unique identifier given a UUID
     *
     * @param uuid from which identifier will be created
     * @return hierarchical identifier
     */
    public String get(final String uuid) {
        final StringJoiner joiner = new StringJoiner("/", "", "/" + uuid);

        IntStream.rangeClosed(0, DEFAULT_COUNT - 1)
                .forEach(x -> joiner.add(uuid.substring(x * DEFAULT_LENGTH, (x + 1) * DEFAULT_LENGTH)));

        return joiner.toString();
    }
}
