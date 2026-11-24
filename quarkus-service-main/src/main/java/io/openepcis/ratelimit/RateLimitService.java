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
package io.openepcis.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.openepcis.config.ApplicationConfigService;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.ratelimit.exception.RateLimitExceededException;
import io.openepcis.service.api.UserDetailsExtractor;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.UriInfo;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RateLimitService {
  private static final OpenEPCISLogger LOGGER = OpenEPCISLogger.getLogger(RateLimitService.class);
  @Context ContainerRequestContext requestContext;
  @Inject SecurityIdentity securityIdentity;
  ApplicationConfigService appConfigService;
  @Inject UserDetailsExtractor userDetailsExtractor;

  // Store the group/role based limit information from application.yml file
  private final Map<String, RateLimitConfig> roleLimitConfig = new ConcurrentHashMap<>();

  // Populate the number of permitted API requests per roles based on the configuration file
  // ratelimit during the set-up
  @Inject
  public void setApplicationConfigService(final ApplicationConfigService appConfigService) {
    this.appConfigService = appConfigService;
    final List<ApplicationConfigService.RateLimit.DefaultRoles> roleConfig =
        appConfigService.ratelimit().defaultroles();

    roleConfig.forEach(
        config -> {
          // Process each role configuration and store it to the HashMap for later usage
          final RateLimitConfig rateLimitConfig =
              new RateLimitConfig(
                  config.permittedUses(),
                  config.timeDuration(),
                  config.durationUnit(),
                  config.shared());
          roleLimitConfig.put(config.role(), rateLimitConfig);
        });
  }

  /**
   * Method invoked during the API request to identify if the user has exceeded the number of API
   * requests per user per defaultGroup basis. Each user will be provided with specific number of
   * API requests based on their defaultGroup. Number of API requests will be based on per User per
   * DefaultGroup basis
   */
  public Uni<Void> tryConsume(final String bucketPrefix) {
    // Obtain user-specific information for getting the token from bucket, else create bucket based
    // on user and role limits.
    final String userID = getUserId();
    final String defaultGroup = getDefaultGroup();
    final String userGroupId = userID + "-" + defaultGroup;
    final RateLimitConfig rateLimitConfig = getRateLimitConfigForGroup(defaultGroup);

    // Obtain the segment of the endpoint such as /capture /queries etc. to append to bucket name
    // based on rateLimitConfig.getShared()
    final String firstSegment =
        Optional.ofNullable(requestContext.getUriInfo())
            .map(UriInfo::getPathSegments)
            .filter(segments -> !segments.isEmpty())
            .map(segments -> segments.get(0))
            .map(PathSegment::getPath)
            .orElse("");

    if (rateLimitConfig != null) {
      final String bucketName =
          (Boolean.TRUE.equals(rateLimitConfig.getShared())
                  ? userGroupId
                  : firstSegment + "-" + userGroupId)
              .trim();
      final Uni<Bucket> bucketUni = getBucket(bucketPrefix + bucketName, rateLimitConfig);

      return bucketUni
          .onItem()
          .transformToUni(
              bucket -> {
                // Consume the tokens from bucket per user per group basis for every API request, if
                // unable to consume then throw exception
                final ConsumptionProbe consumptionProbe = bucket.tryConsumeAndReturnRemaining(1);

                LOGGER.info(
                    "Checking rate limit for userID: {}, defaultGroup: {} and bucket: {} ",
                    userID,
                    defaultGroup,
                    bucketName);
                LOGGER.info("Rate limit consumption : {} ", consumptionProbe);

                if (!consumptionProbe.isConsumed()) {
                  LOGGER.info(
                      "❌ Exceeded allowed number of requests, remaining tokens : {} ",
                      consumptionProbe.getRemainingTokens());
                  throw new RateLimitExceededException(
                      "Rate limit exceeded for user: " + securityIdentity.getPrincipal().getName(),
                      consumptionProbe,
                      consumptionProbe.getRemainingTokens(),
                      firstSegment);
                }
                LOGGER.info("✅ API ratelimit check success.");
                return Uni.createFrom().voidItem(); // Indicates successful consumption
              });
    }
    throw new RateLimitExceededException(
        "Could not find the RateLimit config for the user : "
            + securityIdentity.getPrincipal().getName(),
        null,
        0,
        firstSegment);
  }

  // Method to generate the bucket with userId and defaultGroup with the respective tokens
  // associated to group provided in RateLimitConfig and cache it for reuse.
  @CacheResult(cacheName = "rate-limit-buckets")
  public Uni<Bucket> getBucket(
      @CacheKey final String userGroupId, final RateLimitConfig rateLimitConfig) {
    return Uni.createFrom().item(() -> createBucket(rateLimitConfig));
  }

  // Returns the number of permitted API requests for the defaultGroup to which user is linked to.
  private RateLimitConfig getRateLimitConfigForGroup(final String groupName) {
    return roleLimitConfig.get(groupName);
  }

  // Create the Bandwidth based on the provided time duration and ChronoUnit type
  private Bucket createBucket(final RateLimitConfig rateLimitConfig) {
    final Bandwidth bandwidthBuilder =
        Bandwidth.builder()
            .capacity(rateLimitConfig.getPermittedUses())
            .refillIntervally(
                rateLimitConfig.getPermittedUses(),
                Duration.of(rateLimitConfig.getTimeDuration(), rateLimitConfig.getDurationType()))
            .build();
    return Bucket.builder().addLimit(bandwidthBuilder).build();
  }

  // Method to identify the userId associated with the user making the API request
  private String getUserId() {
    return userDetailsExtractor.getUserId(securityIdentity);
  }

  // Method to identify the default group associated with user making the API request
  private String getDefaultGroup() {
    return userDetailsExtractor.getDefaultGroup(securityIdentity);
  }
}
