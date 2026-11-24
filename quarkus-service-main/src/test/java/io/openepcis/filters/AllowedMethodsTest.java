/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.resource.QueriesResource;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Allow header of a discovery request, read off the resource rather than typed out.
 *
 * <p>Measured on a running repository on 2026-09-28: OPTIONS on every query endpoint answered 204
 * with the GS1 versions and extensions and no Allow at all, though the standard lists it first
 * among the headers such an answer carries.
 */
class AllowedMethodsTest {

    private static Optional<String> allowFor(final String optionsMethodName) {
        final Method options =
                Arrays.stream(QueriesResource.class.getMethods())
                        .filter(m -> m.getName().equals(optionsMethodName))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no such method: " + optionsMethodName));
        return AllowedMethods.of(QueriesResource.class, options);
    }

    @Test
    @DisplayName("the queries collection is read, listed and added to")
    void queriesCollection() {
        // /queries: OPTIONS, GET the list, POST a new named query
        assertEquals(Optional.of("OPTIONS, GET, POST"), allowFor("queriesOptions"));
    }

    @Test
    @DisplayName("a named query is read, replaced and removed")
    void namedQuery() {
        // /queries/{queryName}: PUT is ours and not the standard's, and is named here because it
        // is offered; a client that reads Allow learns what this repository actually answers to.
        assertEquals(Optional.of("OPTIONS, GET, PUT, DELETE"), allowFor("queriesQueryNameOptions"));
    }

    @Test
    @DisplayName("the events of a query are only read")
    void queryEvents() {
        assertEquals(Optional.of("OPTIONS, GET"), allowFor("queriesQueryNameEventsOptions"));
    }

    @Test
    @DisplayName("subscriptions are listed and created")
    void subscriptions() {
        assertEquals(Optional.of("OPTIONS, GET, POST"), allowFor("queriesQueryNameSubscriptionOptions"));
    }

    @Test
    @DisplayName("a single subscription is read and cancelled")
    void oneSubscription() {
        assertEquals(Optional.of("OPTIONS, GET, DELETE"), allowFor("queriesSubscriptionIDOptions"));
    }

    @Test
    @DisplayName("nothing is claimed when nothing matched the request")
    void nothingMatched() throws Exception {
        assertTrue(AllowedMethods.of(null, null).isEmpty());
        assertTrue(AllowedMethods.of(QueriesResource.class, null).isEmpty());
        // a method that is not a resource method at all
        assertTrue(AllowedMethods.of(String.class, String.class.getMethod("trim")).isEmpty());
    }
}
