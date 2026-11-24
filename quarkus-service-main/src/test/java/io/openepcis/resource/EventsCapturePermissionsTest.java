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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.resource.specs.EventsCaptureApi;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.StringPermission;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * POST /events writes an event and must be gated like POST /capture.
 *
 * <p>Until 2026-11-19 the single-event capture asked for {@code query}, so an identity that may
 * only read could write. The roles map onto permissions one to one ({@code
 * quarkus.http.auth.policy.*-policy.permissions}); the identity below is what a query-only caller
 * looks like after that mapping, and the security interceptor answers 403 exactly when {@link
 * SecurityIdentity#checkPermissionBlocking} is false for every permission the method names.
 */
class EventsCapturePermissionsTest {

  private static String[] permissionsOf(final Class<?> type, final String name) {
    final Method method =
        Arrays.stream(type.getMethods())
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no such method: " + name));
    final PermissionsAllowed allowed = method.getAnnotation(PermissionsAllowed.class);
    assertNotNull(allowed, type.getSimpleName() + "." + name + " carries no @PermissionsAllowed");
    return allowed.value();
  }

  private static SecurityIdentity identityWith(final String... permissions) {
    final QuarkusSecurityIdentity.Builder builder =
        QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal("reader"));
    for (final String permission : permissions) {
      builder.addRole(permission).addPermission(new StringPermission(permission));
    }
    return builder.build();
  }

  private static boolean grants(final SecurityIdentity identity, final String[] required) {
    return Arrays.stream(required)
        .anyMatch(p -> identity.checkPermissionBlocking(new StringPermission(p)));
  }

  @Test
  @DisplayName("POST /events asks for capture, on the spec and on the resource")
  void postEventAsksForCapture() {
    assertArrayEquals(new String[] {"capture"}, permissionsOf(EventsCaptureApi.class, "postEvent"));
    assertArrayEquals(
        new String[] {"capture"}, permissionsOf(EventsCaptureResource.class, "postEvent"));
  }

  @Test
  @DisplayName("a query-only identity is refused on POST /events and let in on validate")
  void queryOnlyIdentityIsRefused() {
    final SecurityIdentity reader = identityWith("query");
    assertFalse(grants(reader, permissionsOf(EventsCaptureResource.class, "postEvent")));
    assertTrue(grants(reader, permissionsOf(EventsCaptureResource.class, "validateEvents")));
  }

  @Test
  @DisplayName("a capture identity may post an event")
  void captureIdentityIsLetIn() {
    assertTrue(grants(identityWith("capture"), permissionsOf(EventsCaptureResource.class, "postEvent")));
  }

  @Test
  @DisplayName("validate stays on query")
  void validateStaysOnQuery() {
    assertArrayEquals(new String[] {"query"}, permissionsOf(EventsCaptureApi.class, "validateEvents"));
  }
}
