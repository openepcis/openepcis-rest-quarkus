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

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import io.openepcis.converter.util.IndentingXMLStreamWriter;
import io.openepcis.model.epcis.EPCISDocument;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.EPCISQueryDocument;
import io.openepcis.model.epcis.modifier.CommonExtensionModifier;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import io.openepcis.resource.HeaderConstants;
import io.vertx.core.MultiMap;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.jboss.resteasy.reactive.server.spi.ServerRequestContext;

@Provider
@Produces({MediaType.APPLICATION_XML, MediaType.TEXT_XML, "application/problem+xml"})
public class JakartaJaxbMessageBodyWriter implements MessageBodyWriter<Object> {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JakartaJaxbMessageBodyWriter.class);
  @Inject
  JAXBContext context;
  @Inject
  VersionTransformer versionTransformer;
  @Inject
  ManagedExecutor managedExecutor;
  @Context
  HttpHeaders requestHeaders;

  public JakartaJaxbMessageBodyWriter() {
    log.info("CustomMessageBodyWriter instance created");
  }

  private final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    final boolean isWritable = Stream.of(type.getAnnotations()).anyMatch(a -> XmlRootElement.class.isAssignableFrom(a.annotationType()) || XmlType.class.isAssignableFrom(a.annotationType()));
    return isWritable;
  }

  @Override
  public long getSize(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    // don't bother calculating size by doing pre-flight marshalling
    return -1;
  }

  @Override
  public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws WebApplicationException {
    setContentTypeIfNecessary(httpHeaders, mediaType);
    try {
      // Combine standard EPCIS namespaces with any custom namespaces from the document's events
      final Map<String, String> combinedNamespaceMap = new HashMap<>(EPCISNamespacePrefixMapper.EPCIS_NAMESPACE_MAP);
      extractNamespacesFromDocument(o, combinedNamespaceMap);
      // One marshaller per request: JAXB Marshallers are not thread-safe, and this
      // provider is a singleton — a shared instance let concurrent responses
      // overwrite each other's prefix mapper and formatting properties.
      final Marshaller marshaller = context.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      marshaller.setProperty(Marshaller.JAXB_FRAGMENT, false);
      marshaller.setProperty(MarshallerProperties.NAMESPACE_PREFIX_MAPPER, combinedNamespaceMap);
      final boolean addStylesheet = requestHeaders.getRequestHeader("Accept").stream().map(String::toLowerCase).anyMatch(s -> s.indexOf("application/xhtml+xml") != -1);
      if (addStylesheet) {
        log.debug("adding stylesheet /xsl/render-xml-template.xsl");
      }
      if (wantsVersion12(httpHeaders)) {
        // EPCIS 1.2 XML is derived from the 2.0 document by the version transformer, so the
        // service needs no particular XML transformer implementation: the XSLT one of the public
        // converter suffices, a streaming one is picked up through the ServiceLoader when present.
        // The 2.0 document is buffered first; a response is bounded by the per-page limits.
        final ByteArrayOutputStream buffered = new ByteArrayOutputStream();
        final XMLStreamWriter writer = documentWriter(buffered, addStylesheet);
        marshaller.marshal(o, writer);
        writer.flush();
        try (InputStream converted = versionTransformer.convert(new ByteArrayInputStream(buffered.toByteArray()), Conversion.of(EPCISFormat.XML, EPCISVersion.VERSION_2_0_0, EPCISFormat.XML, EPCISVersion.VERSION_1_2_0))) {
          converted.transferTo(entityStream);
        }
      } else {
        final XMLStreamWriter writer = documentWriter(entityStream, addStylesheet);
        marshaller.marshal(o, writer);
        writer.flush();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Whether the response announces EPCIS 1.2, which the resource decided from the request headers. */
  private static boolean wantsVersion12(final MultivaluedMap<String, Object> httpHeaders) {
    if (!httpHeaders.containsKey(HeaderConstants.GS1_EPCIS_VERSION)) {
      return false;
    }
    final Optional<EPCISVersion> epcisVersion = EPCISVersion.fromString(httpHeaders.getFirst(HeaderConstants.GS1_EPCIS_VERSION).toString());
    return epcisVersion.isPresent() && epcisVersion.get().equals(EPCISVersion.VERSION_1_2_0);
  }

  /** An indenting writer on {@code out} that announces the rendering stylesheet when asked for. */
  private XMLStreamWriter documentWriter(final OutputStream out, final boolean addStylesheet) throws XMLStreamException {
    return new IndentingXMLStreamWriter(xmlOutputFactory.createXMLStreamWriter(out)) {
      private void writeStylesheetPI() throws XMLStreamException {
        if (addStylesheet) {
          writeProcessingInstruction("xml-stylesheet", "type=\"text/xsl\" href=\"/xsl/render-xml-template.xsl\"");
          writeCharacters("\n");
        }
      }
      @Override
      public void writeStartDocument() throws XMLStreamException {
        super.writeStartDocument();
        writeStylesheetPI();
      }
      @Override
      public void writeStartDocument(String version) throws XMLStreamException {
        super.writeStartDocument(version);
        writeStylesheetPI();
      }
      @Override
      public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        super.writeStartDocument(encoding, version);
        writeStylesheetPI();
      }
    };
  }

  private void setContentTypeIfNecessary(final MultivaluedMap<String, Object> httpHeaders, final MediaType mediaType) {
    Object contentType = httpHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
    if (isNotXml(contentType)) {
      if (mediaType.getSubtype().contains("xml")) {
        httpHeaders.putSingle(HttpHeaders.CONTENT_TYPE, mediaType.toString());
      } else {
        httpHeaders.putSingle(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
      }
    }
  }

  private void setContentTypeIfNecessary(ServerRequestContext context) {
    String currentContentType = null;
    Iterable<Map.Entry<String, String>> responseHeaders = context.serverResponse().getAllResponseHeaders();
    if (responseHeaders instanceof MultiMap) {
      currentContentType = ((MultiMap) responseHeaders).get(HttpHeaders.CONTENT_TYPE);
    } else {
      for (Map.Entry<String, String> entry : responseHeaders) {
        if (entry.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) {
          currentContentType = entry.getValue();
          break;
        }
      }
    }
    if (isNotXml(currentContentType)) {
      context.serverResponse().setResponseHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
    }
  }

  private boolean isNotXml(Object contentType) {
    return contentType == null || !contentType.toString().contains("xml");
  }

  /**
   * Extracts custom namespace mappings from the document being marshalled.
   * Uses CommonExtensionModifier.populateNamespaces() to extract from context lists.
   *
   * @param document the object being marshalled
   * @param namespaceMap the map to populate with namespace URI -> prefix mappings
   */
  private void extractNamespacesFromDocument(Object document, Map<String, String> namespaceMap) {
    final ConversionNamespaceContext nsContext = new ConversionNamespaceContext();
    if (document instanceof EPCISDocument epcisDoc) {
      // EPCISDocument: collect all event contextInfo into one list, then populate once
      // (populateNamespaces resets on each call, so we must combine first)
      if (epcisDoc.getEpcisBody() != null && epcisDoc.getEpcisBody().getEventList() != null) {
        final List<Object> combinedContext = new ArrayList<>();
        for (EPCISEvent event : epcisDoc.getEpcisBody().getEventList()) {
          if (event.getContextInfo() != null) {
            combinedContext.addAll(event.getContextInfo());
          }
        }
        CommonExtensionModifier.populateNamespaces(combinedContext, nsContext);
      }
    } else if (document instanceof EPCISQueryDocument queryDoc) {
      // EPCISQueryDocument: extract from document-level context (already aggregated from events)
      CommonExtensionModifier.populateNamespaces(queryDoc.getContext(), nsContext);
    }
    // Add extracted namespaces to the map (getAllNamespaces returns URI -> prefix)
    namespaceMap.putAll(nsContext.getAllNamespaces());
  }
}
