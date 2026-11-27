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

/**
 * A resource class that adds methods to a path another resource class already serves.
 *
 * <p>An edition can offer an operation on a path the Community Edition owns, for example
 * {@code PUT /queries/{queryName}} next to the standard {@code GET} and {@code DELETE}. The
 * discovery answer to {@code OPTIONS} on that path is built from the class that served it, which
 * knows nothing of the addition; implementing this interface makes {@link AllowedMethods} count
 * the added methods in the {@code Allow} header too. The implementing class must carry the same
 * class-level {@code @Path} as the resource it extends.
 */
public interface ResourceExtension {

  /** The resource class whose paths this class adds methods to. */
  Class<?> extendedResource();
}
