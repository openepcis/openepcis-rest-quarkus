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
package io.openepcis.wsapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.subscription.WebSocketRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueriesWebSocketRecordTest {

    @Test
    @DisplayName("a result written by WebSocketDelivery is read back with its session and its body intact")
    void readsWhatTheDeliveryWrites() throws Exception {
        final String body = "{\"type\":\"EPCISQueryDocument\",\"queryName\":\"factory-ore-mined\"}";
        final String wire = new ObjectMapper().writeValueAsString(new WebSocketRecord("session-1", body));

        final WebSocketRecord read = QueriesWebSocket.readRecord(wire);

        assertEquals("session-1", read.sessionID());
        assertEquals(body, read.result());
    }

    @Test
    @DisplayName("an unreadable message is skipped, so it cannot end the stream for every session")
    void skipsUnreadable() {
        assertNull(QueriesWebSocket.readRecord("not json"));
    }
}
