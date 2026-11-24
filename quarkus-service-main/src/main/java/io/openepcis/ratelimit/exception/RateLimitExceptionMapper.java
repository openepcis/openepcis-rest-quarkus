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
package io.openepcis.ratelimit.exception;

import io.openepcis.model.rest.ProblemResponseBody;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.reactive.RestResponse;

/***
 * Custom ExceptionMapper class to override response during RateLimitingInterceptor.RateLimitExceededException
 * When user exceeds the number of API request permitted for them within a permitted span of time then this class kicks-in and \
 * the exception messages from here are shown during the RateLimitingInterceptor.RateLimitExceededException.
 */
@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitExceededException> {
  /**
   * Method to override the default RateLimitingInterceptor.RateLimitExceededException and generate
   * Response
   *
   * @param exception the exception to map to a response.
   * @return the Response with custom message
   */
  @Override
  public Response toResponse(final RateLimitExceededException exception) {
    // Convert the nanoseconds refill time to seconds
    final double waitTimeInSeconds =
        (double) exception.getConsumptionProbe().getNanosToWaitForRefill() / 1_000_000_000;

    final ProblemResponseBody problemResponseBody =
        ProblemResponseBody.fromException(exception, RestResponse.Status.TOO_MANY_REQUESTS)
            .detail(
                "You have exceeded the allowed number of requests. Please wait and try again after : "
                    + waitTimeInSeconds
                    + " seconds")
            .instance(exception.getUrlEndpoint());

    // Build the response with header information and body
    return Response.status(Response.Status.TOO_MANY_REQUESTS)
        .entity(problemResponseBody)
        .header("RateLimit-Endpoint", exception.getUrlEndpoint())
        .header("RateLimit-Limit", exception.getPermittedUses())
        .header("RateLimit-Remaining", exception.getConsumptionProbe().getRemainingTokens())
        .header("Retry-After", waitTimeInSeconds + " seconds")
        .build();
  }
}
