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

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Which methods a resource answers to, read off the resource itself.
 *
 * <p>EPCIS 2.0 has every discovery request answer with an Allow header naming them, and ours sent
 * none. A hand-written table would be one more thing to forget when an endpoint is added or
 * dropped, so this reads the JAX-RS annotations of the class that just served the request and
 * gathers every sibling method sitting on the same path. The annotations live on the interfaces
 * that declare the API, which is why the whole hierarchy is walked.
 */
public final class AllowedMethods {

  /** The order the standard writes them in, with the rest after. */
  private static final List<String> ORDER = List.of("OPTIONS", "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");

  private AllowedMethods() {}

  /**
   * @return "OPTIONS, GET, POST" and the like, or empty when the request matched no resource
   *     method and there is nothing honest to say.
   */
  public static Optional<String> of(final Class<?> resourceClass, final Method matched) {
    return of(resourceClass, matched, List.of());
  }

  /**
   * As {@link #of(Class, Method)}, counting the methods of {@code extensions} as well: classes
   * that add methods to the paths of {@code resourceClass} (see {@link ResourceExtension}).
   */
  public static Optional<String> of(
      final Class<?> resourceClass, final Method matched, final Collection<Class<?>> extensions) {
    if (resourceClass == null || matched == null) {
      return Optional.empty();
    }
    final Set<Class<?>> hierarchy = hierarchyOf(resourceClass);
    final String path = pathOf(hierarchy, matched);
    final Set<Class<?>> searched = new LinkedHashSet<>(hierarchy);
    extensions.forEach(extension -> searched.addAll(hierarchyOf(extension)));
    final Set<String> found = new LinkedHashSet<>();
    for (final Class<?> type : searched) {
      for (final Method candidate : type.getDeclaredMethods()) {
        if (Objects.equals(pathOf(candidate), path)) {
          verbOf(candidate).ifPresent(found::add);
        }
      }
    }
    if (found.isEmpty()) {
      return Optional.empty();
    }
    final List<String> ordered = new ArrayList<>(ORDER.stream().filter(found::contains).toList());
    found.stream().filter(verb -> !ORDER.contains(verb)).sorted().forEach(ordered::add);
    return Optional.of(String.join(", ", ordered));
  }

  /**
   * The sub-path of the method that was actually invoked.
   *
   * <p>What runs is the implementation, and the implementation carries no annotations: they are
   * on the interface that declares the API. So the same signature is looked for everywhere in the
   * hierarchy until one is found that says where it sits.
   */
  private static String pathOf(final Set<Class<?>> hierarchy, final Method matched) {
    for (final Class<?> type : hierarchy) {
      try {
        final Path path = type.getDeclaredMethod(matched.getName(), matched.getParameterTypes()).getAnnotation(Path.class);
        if (path != null) {
          return normalise(path.value());
        }
      } catch (final NoSuchMethodException notHere) {
        // the signature is simply not declared by this type
      }
    }
    return "";
  }

  /** The sub-path a method declares itself, or the root of the resource when it declares none. */
  private static String pathOf(final Method method) {
    final Path path = method.getAnnotation(Path.class);
    if (path == null) {
      return "";
    }
    return normalise(path.value());
  }

  /** Without the leading and trailing slashes that vary by author. */
  private static String normalise(final String path) {
    return path.replaceAll("^/+", "").replaceAll("/+$", "");
  }

  /**
   * The HTTP method a resource method answers to. JAX-RS marks @GET, @POST and the rest with
   * {@link HttpMethod}, including any an application defines itself, so asking for that marker
   * finds them all without naming a single one of them here.
   */
  private static Optional<String> verbOf(final Method method) {
    for (final Annotation annotation : method.getAnnotations()) {
      final HttpMethod marker = annotation.annotationType().getAnnotation(HttpMethod.class);
      if (marker != null) {
        return Optional.of(marker.value());
      }
    }
    return Optional.empty();
  }

  /** The class, what it extends, and every interface any of them implements. */
  private static Set<Class<?>> hierarchyOf(final Class<?> type) {
    final Set<Class<?>> seen = new LinkedHashSet<>();
    collect(type, seen);
    return seen;
  }

  private static void collect(final Class<?> type, final Set<Class<?>> seen) {
    if (type == null || type == Object.class || !seen.add(type)) {
      return;
    }
    collect(type.getSuperclass(), seen);
    for (final Class<?> implemented : type.getInterfaces()) {
      collect(implemented, seen);
    }
  }
}
