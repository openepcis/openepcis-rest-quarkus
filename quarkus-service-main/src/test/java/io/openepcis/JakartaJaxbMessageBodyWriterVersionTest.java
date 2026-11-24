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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openepcis.converter.VersionTransformer;
import io.openepcis.model.epcis.EPCISQueryBody;
import io.openepcis.model.epcis.EPCISQueryDocument;
import io.openepcis.model.epcis.EpcisQueryResult;
import io.openepcis.model.epcis.QueryResultsBody;
import io.openepcis.quarkus.model.OpenEPCISJAXBContextProducer;
import io.openepcis.resource.HeaderConstants;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import jakarta.xml.bind.JAXBContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EPCIS 1.2 XML is derived through the version transformer, so the service does not need the
 * streaming SAX implementation; the XSLT transformer of the public converter is enough.
 */
class JakartaJaxbMessageBodyWriterVersionTest {

  private static final String EPCIS_2_0 = "urn:epcglobal:epcis:xsd:2";
  private static final String EPCIS_1_2 = "urn:epcglobal:epcis:xsd:1";

  private JakartaJaxbMessageBodyWriter writer;

  @BeforeEach
  void setUp() throws Exception {
    writer = new JakartaJaxbMessageBodyWriter();
    // The same MOXy context the application produces, and the transformer built on it.
    final JAXBContext context = new OpenEPCISJAXBContextProducer().createJAXBContext();
    writer.context = context;
    writer.versionTransformer = new VersionTransformer(Executors.newFixedThreadPool(2), context);
    final HttpHeaders request = mock(HttpHeaders.class);
    when(request.getRequestHeader("Accept")).thenReturn(List.of(MediaType.APPLICATION_XML));
    writer.requestHeaders = request;
  }

  @Test
  @DisplayName("without a version header the document is written as EPCIS 2.0")
  void writesVersion20ByDefault() throws Exception {
    final String xml = write(new MultivaluedHashMap<>());
    assertTrue(xml.contains(EPCIS_2_0), xml);
    assertFalse(xml.contains(EPCIS_1_2), xml);
  }

  @Test
  @DisplayName("GS1-EPCIS-Version 1.2.0 on the response yields an EPCIS 1.2 document")
  void writesVersion12OnRequest() throws Exception {
    final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    headers.putSingle(HeaderConstants.GS1_EPCIS_VERSION, "1.2.0");
    final String xml = write(headers);
    assertTrue(xml.contains(EPCIS_1_2), xml);
    assertFalse(xml.contains(EPCIS_2_0), xml);
  }

  @Test
  @DisplayName("the streaming SAX converter is not on the class path of this module")
  void saxConverterAbsent() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("io.openepcis.converter.sax.SAXVersionTransformerFactory"));
  }

  private String write(final MultivaluedMap<String, Object> responseHeaders) throws Exception {
    final EPCISQueryDocument document =
        new EPCISQueryDocument(
            new EPCISQueryBody(
                new EpcisQueryResult(
                    null, "SimpleEventQuery", null, new QueryResultsBody(List.of()))),
            Optional.of("2.0.0"));
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    writer.writeTo(
        document,
        EPCISQueryDocument.class,
        EPCISQueryDocument.class,
        null,
        MediaType.APPLICATION_XML_TYPE,
        responseHeaders,
        out);
    return out.toString(StandardCharsets.UTF_8);
  }
}
