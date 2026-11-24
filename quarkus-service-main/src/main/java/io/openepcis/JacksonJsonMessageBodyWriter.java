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
package io.openepcis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import org.jboss.resteasy.reactive.server.jackson.JacksonMessageBodyWriterUtil;
import org.jboss.resteasy.reactive.server.spi.ResteasyReactiveResourceInfo;
import org.jboss.resteasy.reactive.server.spi.ServerMessageBodyWriter;
import org.jboss.resteasy.reactive.server.spi.ServerRequestContext;

/***
 * high-priority MessageBodyWriter required to overrule Moxy implementation, which is only good for XML
 */
@Provider
@Produces({"application/json", "application/ld+json", "*/*"})
@Consumes({"application/json", "*/*"})
@Priority(5001)
public class JacksonJsonMessageBodyWriter implements ServerMessageBodyWriter<Object> {

  private final ObjectWriter defaultWriter;

  @Inject
  public JacksonJsonMessageBodyWriter(ObjectMapper mapper) {
    this.defaultWriter = JacksonMessageBodyWriterUtil.createDefaultWriter(mapper);
  }

  @Override
  public boolean isWriteable(
      Class<?> type, Type genericType, ResteasyReactiveResourceInfo target, MediaType mediaType) {
    return !mediaType.getSubtype().toLowerCase().contains("xml");
  }

  public void writeResponse(Object o, Type genericType, ServerRequestContext context)
      throws WebApplicationException, IOException {
    OutputStream stream = context.getOrCreateOutputStream();
    if (o instanceof String) {
      stream.write(((String) o).getBytes(StandardCharsets.UTF_8));
    } else {
      this.defaultWriter.writeValue(stream, o);
    }

    stream.close();
  }

  @Override
  public boolean isWriteable(
      Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
    return !mediaType.getSubtype().toLowerCase().contains("xml");
  }

  public void writeTo(
      Object o,
      Class<?> type,
      Type genericType,
      Annotation[] annotations,
      MediaType mediaType,
      MultivaluedMap<String, Object> httpHeaders,
      OutputStream entityStream)
      throws IOException, WebApplicationException {
    JacksonMessageBodyWriterUtil.doLegacyWrite(
        o, annotations, httpHeaders, entityStream, this.defaultWriter);
  }
}
