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

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
@OpenEPCISRateLimiter
public class RateLimitingInterceptor {
  @Inject RateLimitService rateLimitService;

  @AroundInvoke
  public Object checkRateLimit(final InvocationContext context) {
    final OpenEPCISRateLimiter methodLimiter =
        context.getMethod().getAnnotation(OpenEPCISRateLimiter.class);
    return rateLimitService
        .tryConsume(methodLimiter.bucketPrefix())
        .onItem()
        .transformToUni(
            v -> {
              try {
                // Return the context for further execution
                return (Uni<?>) context.proceed();
              } catch (Exception e) {
                return Uni.createFrom().failure(e);
              }
            });
  }
}
