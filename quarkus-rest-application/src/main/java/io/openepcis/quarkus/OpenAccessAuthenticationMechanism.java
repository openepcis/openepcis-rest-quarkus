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
package io.openepcis.quarkus;

import io.openepcis.repository.config.RepositoryConfiguration;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The Community Edition has no authentication: every request is the configured default user
 * ({@code openepcis.user.default-user}) with the roles {@code capture}, {@code query} and {@code
 * admin}. Anyone who can reach the service can read, write and delete everything. It is meant for
 * trying OpenEPCIS out, not for production.
 *
 * <p>Only built into the Community Edition ({@code openepcis.edition=ce}); the Business edition
 * sets another edition and authenticates with its own mechanisms. The edition is checked again on
 * every request, so a build that got this far by mistake still grants nothing.
 */
@ApplicationScoped
@IfBuildProperty(name = "openepcis.edition", stringValue = "ce")
public class OpenAccessAuthenticationMechanism implements HttpAuthenticationMechanism {

  static final Set<String> ROLES = Set.of("capture", "query", "admin");

  private final RepositoryConfiguration repositoryConfiguration;
  private final String edition;

  public OpenAccessAuthenticationMechanism(
      final RepositoryConfiguration repositoryConfiguration,
      @ConfigProperty(name = "openepcis.edition") final String edition) {
    this.repositoryConfiguration = repositoryConfiguration;
    this.edition = edition;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      final RoutingContext context, final IdentityProviderManager identityProviderManager) {
    if (!"ce".equals(edition)) {
      return Uni.createFrom().nullItem();
    }
    return Uni.createFrom()
        .item(
            QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(repositoryConfiguration.user().defaultUser()))
                .addRoles(ROLES)
                .build());
  }

  @Override
  public Uni<ChallengeData> getChallenge(final RoutingContext context) {
    return Uni.createFrom().nullItem();
  }

  @Override
  public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
    return Set.of();
  }
}
