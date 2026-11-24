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

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueriesWebSocketPingTest {

    private static Session session(final String id, final boolean open, final List<String> pinged, final boolean failing) {
        final RemoteEndpoint.Async remote = (RemoteEndpoint.Async) Proxy.newProxyInstance(
                QueriesWebSocketPingTest.class.getClassLoader(), new Class<?>[] {RemoteEndpoint.Async.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendPing")) {
                        if (failing) {
                            throw new IllegalStateException("socket already gone");
                        }
                        assertEquals(0, ((ByteBuffer) args[0]).remaining());
                        pinged.add(id);
                    }
                    return null;
                });
        return (Session) Proxy.newProxyInstance(
                QueriesWebSocketPingTest.class.getClassLoader(), new Class<?>[] {Session.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "isOpen" -> open;
                    case "getAsyncRemote" -> remote;
                    default -> null;
                });
    }

    @Test
    @DisplayName("every open socket is pinged, a closed or broken one is skipped without stopping the rest")
    void pingsOpenSessions() {
        final QueriesWebSocket endpoint = new QueriesWebSocket(null, null, null, null);
        final List<String> pinged = new ArrayList<>();
        endpoint.sessions.put("a", session("a", true, pinged, false));
        endpoint.sessions.put("b", session("b", false, pinged, false));
        endpoint.sessions.put("c", session("c", true, pinged, true));
        endpoint.sessions.put("d", session("d", true, pinged, false));

        final int count = endpoint.pingOpenSessions();

        assertEquals(2, count);
        assertEquals(List.of("a", "d"), pinged.stream().sorted().toList());
    }
}
