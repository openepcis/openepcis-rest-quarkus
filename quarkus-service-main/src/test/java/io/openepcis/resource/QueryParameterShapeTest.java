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
package io.openepcis.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which unknown parameters are refused, and which are let by.
 *
 * <p>An unknown parameter used to be dropped without a word, whatever it was.
 * For a parameter left over from an OIDC redirect that is right — it has nothing
 * to do with the query. For a misspelt filter it is the worst possible answer:
 * the query runs as though no filter had been given, and the caller cannot tell
 * a full result from a filtered one. Measured on a running repository on
 * 2026-08-29: {@code EQ_voelligerUnsinn=42} and a {@code GE_errorDeclarationTime}
 * missing its underscore both returned exactly the unfiltered set.
 */
class QueryParameterShapeTest {

    private static boolean refused(final String parameter) {
        return RestResourceUtil.looksLikeAFilter(parameter);
    }

    @Test
    @DisplayName("a misspelt filter is refused, whatever the operator")
    void misspeltFiltersAreRefused() {
        // One underscore short of GE_errorDeclaration_Time — the typo that
        // silently answered with everything.
        assertTrue(refused("GE_errorDeclarationTime"));
        assertTrue(refused("EQ_voelligerUnsinn"));
        assertTrue(refused("EXISTS_errorDeclarationn"));
        assertTrue(refused("MATCH_epcs"));
        assertTrue(refused("LT_recordTimee"));
        assertTrue(refused("EQ_INNER_something"));
    }

    @Test
    @DisplayName("what an OIDC redirect leaves behind is not a filter and stays ignored")
    void redirectLeftoversAreIgnored() {
        assertFalse(refused("code"));
        assertFalse(refused("state"));
        assertFalse(refused("session_state"));
        assertFalse(refused("iss"));
        assertFalse(refused("error"));
        assertFalse(refused("error_description"));
    }

    @Test
    @DisplayName("nor is the paging, which is not a filter either")
    void pagingIsNotAFilter() {
        assertFalse(refused("perPage"));
        assertFalse(refused("nextPageToken"));
    }
}
