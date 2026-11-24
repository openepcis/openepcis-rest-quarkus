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

import io.openepcis.capture.api.CaptureEventManagementService;
import io.openepcis.capture.api.CaptureEventService;
import io.openepcis.config.ApplicationConfigService;
import io.openepcis.constants.CBVVersion;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.core.model.PaginationSupport;
import io.openepcis.custom.extension.cache.UserExtensionSchemaCache;
import io.openepcis.model.dto.EPCPageResult;
import io.openepcis.model.dto.QueryResult;
import io.openepcis.model.dto.TopLevelResource;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.EPCISQueryBody;
import io.openepcis.model.epcis.EPCISQueryDocument;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.model.epcis.exception.NotAcceptedException;
import io.openepcis.model.epcis.exception.QueryParameterException;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.query.context.DefaultQueryContext;
import io.openepcis.query.context.DefaultSubscriptionContext;
import io.openepcis.query.context.QueryContext;
import io.openepcis.query.context.SubscriptionContext;
import io.openepcis.query.data.QueryParamName;
import io.openepcis.query.data.ValueType;
import io.openepcis.service.api.UserDetailsExtractor;
import io.openepcis.service.context.DefaultEPCISContext;
import io.openepcis.service.context.EPCISContext;
import io.openepcis.service.util.CaptureErrorBehavior;
import io.openepcis.service.util.Constants;
import io.openepcis.service.util.QueryDocumentUtil;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static io.openepcis.repository.Constants.*;
import static io.openepcis.service.util.Constants.COLLECTION;

@ApplicationScoped
public class RestResourceUtil {
  public static final Set<String> EPC_FORMATS = Set.of(EPCFormat.values()).stream().map(EPCFormat::name).collect(Collectors.toSet());
  public static final Set<String> CBV_FORMATS = Set.of(CBVFormat.values()).stream().map(CBVFormat::name).collect(Collectors.toSet());
  public static final Set<String> CAPTURE_ERROR_BEHAVIOUR = Set.of(CaptureErrorBehavior.values()).stream().map(CaptureErrorBehavior::name).collect(Collectors.toSet());
  public static final String FORMAT_NEXT_PAGE_TOKEN = "?nextPageToken=%s";
  private final ApplicationConfigService appConfigService;
  private final CaptureEventManagementService captureEventManagementService;
  private final UserExtensionSchemaCache userExtensionSchemaCache;
  private final UserDetailsExtractor userDetailsExtractor;
  @Inject
  HttpServerRequest request;
  @Context
  UriInfo uriInfo;
  @Context
  SecurityIdentity securityIdentity;

  public Response.ResponseBuilder addDiscoveryOptionHeaders(final Response.ResponseBuilder responseBuilder) {
    return addDiscoveryOptionHeaders(Optional.empty(), responseBuilder);
  }

  public Response.ResponseBuilder addDiscoveryOptionHeaders(final Optional<CaptureEventService> captureEventService, final Response.ResponseBuilder responseBuilder) {
    getDiscoverOptionHeaders(captureEventService).entrySet().stream().forEach(entry -> {
      responseBuilder.header(entry.getKey(), entry.getValue());
    });
    return responseBuilder;
  }

  public Map<String, String> getDiscoverOptionHeaders() {
    return getDiscoverOptionHeaders(Optional.empty());
  }

  public Map<String, String> getDiscoverOptionHeaders(final Optional<CaptureEventService> captureEventService) {
    final Map<String, String> headers = new HashMap<>();
    headers.put(CommonConstants.GS1_EPCIS_VERSION, captureEventManagementService.getEPCISVersion().getVersion());
    headers.put(CommonConstants.GS1_EPCIS_MIN, captureEventManagementService.getEPCISVersionMin().getVersion());
    headers.put(CommonConstants.GS1_EPCIS_MAX, captureEventManagementService.getEPCISVersionMax().getVersion());
    headers.put(CommonConstants.GS1_CBV_VERSION, appConfigService.gs1().cbv().version());
    headers.put(CommonConstants.GS1_CBV_MIN, appConfigService.gs1().cbv().min());
    headers.put(CommonConstants.GS1_CBV_MAX, appConfigService.gs1().cbv().max());
    headers.put(CommonConstants.GS1_EPC_FORMAT, String.join(",", EPC_FORMATS));
    if ((getContentType().isPresent() && (getContentType().get().toLowerCase().contains("/xml") || getContentType().get().toLowerCase().contains("+xml"))) || (getAcceptHeader().isPresent()) && (getAcceptHeader().get().toLowerCase().contains("/xml") || getAcceptHeader().get().toLowerCase().contains("+xml"))) {
      headers.put("GS1-CBV-XML-Format", String.join(",", CBV_FORMATS));
    }
    if (captureEventService.isPresent()) {
      final CaptureEventService eventService = captureEventService.get();
      headers.put(CommonConstants.GS1_EPCIS_Capture_Limit, eventService.getCaptureLimit().toString());
      headers.put(CommonConstants.GS1_EPCIS_Capture_File_Size_Limit, BigDecimal.valueOf(eventService.getCaptureFileSizeLimit()).setScale(0, RoundingMode.DOWN).toPlainString());
      headers.put(CommonConstants.GS1_Capture_Error_Behaviour, String.join(",", CAPTURE_ERROR_BEHAVIOUR));
    }
    headers.put(HeaderConstants.GS1_EXTENSIONS, getSupportedGS1Extensions());
    headers.put(CommonConstants.GS1_Vendor_VERSION, appConfigService.gs1().vendor().version());
    return headers;
  }

  public Optional<String> getContentType() {
    return getRequest().map(r -> r.headers().get(HttpHeaders.CONTENT_TYPE));
  }

  public Optional<HttpServerRequest> getRequest() {
    return Optional.of(request);
  }

  public Optional<HttpMethod> getHttpMethod() {
    return getRequest().map(r -> r.method());
  }

  public Optional<String> getRequestURL() {
    return getRequest().map(r -> r.uri().toString());
  }

  public Optional<String> getAcceptHeader() {
    return getRequest().map(r -> r.headers().get(HttpHeaders.ACCEPT));
  }

  public Optional<Long> getContentLength() {
    final Optional<String> h = getRequest().map(r -> r.headers().get(HttpHeaders.CONTENT_LENGTH));
    return h.map(Long::parseLong);
  }

  /** Who is calling, as the edition's {@link UserDetailsExtractor} reads it. */
  public String getUserId(SecurityIdentity securityIdentity) {
    return userDetailsExtractor.getUserId(securityIdentity);
  }

  public static Set<String> getUserRoles(SecurityIdentity securityIdentity) {
    return securityIdentity.getRoles();
  }

  public EPCISContext epcisContext() {
    MultiMap requestHeaders = request.headers();
    Optional<String> epcisVersion = Objects.isNull(requestHeaders.get(HeaderConstants.GS1_EPCIS_VERSION)) ? Optional.of("2.0.1") : Optional.of(requestHeaders.get(HeaderConstants.GS1_EPCIS_VERSION));
    Optional<String> epcisVersionMin = Objects.isNull(requestHeaders.get(HeaderConstants.GS1_EPCIS_MIN)) ? Optional.of("1.2.0") : Optional.of(requestHeaders.get(HeaderConstants.GS1_EPCIS_MIN));
    Optional<String> epcisVersionMax = Objects.isNull(requestHeaders.get(HeaderConstants.GS1_EPCIS_MAX)) ? Optional.of("2.0.1") : Optional.of(requestHeaders.get(HeaderConstants.GS1_EPCIS_MAX));
    Optional<String> cbvVersion = Objects.isNull(requestHeaders.get(HeaderConstants.GS1_CBV_VERSION)) ? Optional.of("2.0.0") : Optional.of(requestHeaders.get(HeaderConstants.GS1_CBV_VERSION));
    Optional<String> cbvVersionMin = Objects.isNull(requestHeaders.get(HeaderConstants.GS1_CBV_MIN)) ? Optional.of("1.2.2") : Optional.of(requestHeaders.get(HeaderConstants.GS1_CBV_MIN));
    Optional<String> cbvVersionMax = Objects.isNull(requestHeaders.get(HeaderConstants.GS1_CBV_MAX)) ? Optional.of("2.0.1") : Optional.of(requestHeaders.get(HeaderConstants.GS1_CBV_MAX));
    Optional<String> epcFormat = Objects.isNull(requestHeaders.get(HeaderConstants.GS1_EPC_FORMAT)) ? Optional.of("Never_Translates") : Optional.of(requestHeaders.get(HeaderConstants.GS1_EPC_FORMAT));
    Optional<String> cbvFormat = Objects.isNull(requestHeaders.get(HeaderConstants.GS1_CBV_XML_FORMAT)) ? Optional.of("Never_Translates") : Optional.of(requestHeaders.get(HeaderConstants.GS1_CBV_XML_FORMAT));
    final EPCISFormat epcisFormat = EPCISFormat.fromString(getAcceptHeader().orElse(getContentType().orElse(""))).orElse(EPCISFormat.JSON_LD);
    final EPCISContext epcisContext = new DefaultEPCISContext(epcisFormat.getMediaType(), epcisVersion, epcisVersionMin, epcisVersionMax, cbvVersion, cbvVersionMin, cbvVersionMax, epcFormat, cbvFormat);
    return epcisContext;
  }

  /**
   * The shape of something meant as an EPCIS filter.
   *
   * <p>Deliberately about the <em>shape</em> and not the list of known names:
   * whether a parameter was meant as a filter is visible from its operator
   * prefix, and that is what separates a typo worth refusing from a stray
   * redirect parameter worth ignoring.
   */
  private static final Pattern LOOKS_LIKE_A_FILTER =
      Pattern.compile("^(EQ|NEQ|GT|GE|LT|LE|EXISTS|MATCH|WD|HASATTR|EQATTR)(_INNER)?_.+");

  static boolean looksLikeAFilter(final String parameterName) {
    return parameterName != null && LOOKS_LIKE_A_FILTER.matcher(parameterName).matches();
  }

  public QueryContext queryContext() {
    try {
      final Map<String, Object> params = new HashMap<>();
      params.put(Constants.VISIBLE, true);
      uriInfo.getQueryParameters().entrySet().forEach(e -> {
        try {
          List<String> values = e.getValue().stream().flatMap(v -> Arrays.stream(v.split(","))).toList();
          params.put(e.getKey(), getRequiredValueType(e.getKey(), values));
        } catch (IllegalArgumentException ex) {
          // Two very different things arrive here, and treating them alike was
          // the mistake. A parameter left over from an OIDC redirect (code,
          // state, session_state) has nothing to do with the query and must not
          // break it — that is what this catch was for. A misspelt *filter* is
          // the opposite case: dropping it answers the query as though no filter
          // had been asked for, and the caller cannot tell a full result from a
          // filtered one. Measured on a running repository: EQ_voelligerUnsinn=42
          // and a GE_errorDeclarationTime missing its underscore both returned
          // exactly the unfiltered set.
          if (looksLikeAFilter(e.getKey())) {
            throw new QueryParameterException(String.format(
                "'%s' is not an EPCIS query parameter. It was refused rather than ignored:"
                    + " ignoring it would have answered as though no filter had been given.",
                e.getKey()));
          }
        }
      });
      EPCISContext epcisContext = epcisContext();
      String gs1Extensions = request.getHeader(HeaderConstants.GS1_EXTENSIONS);
      if (StringUtils.isNotBlank(gs1Extensions)) {
        epcisContext.getGs1Extensions().set(Optional.of(gs1Extensions));
      }
      return new DefaultQueryContext(epcisContext, params);
    } catch (RuntimeException e) {
      throw new QueryParameterException(e.getMessage(), e);
    }
  }

  @Deprecated
  public void verifyGS1Headers(String epcisVersion, Optional<String> epcisMin, Optional<String> epcisMax, Optional<String> cbvMin, Optional<String> cbvMax, Optional<String> cbvVersion) {
  }

  public void validateFormatOfGS1Headers(String epcisVersion, Optional<String> epcisMin, Optional<String> epcisMax, Optional<String> cbvMin, Optional<String> cbvMax, Optional<String> cbvVersion, Optional<String> epcFormat, Optional<String> cbvFormat) {
    final Map<String, String> headers = getHeadersMap(epcisVersion, epcisMin, epcisMax, cbvMin, cbvMax, cbvVersion);
    if (EPCISVersion.fromString(epcisVersion).isEmpty()) {
      throw new NotAcceptedException(String.format("GS1-EPCIS-Version shall be one of %s", String.join(",", Stream.of(EPCISVersion.values()).map(EPCISVersion::getVersion).toList())));
    }
    if (epcFormat.isPresent()) {
      try {
        EPCFormat.valueOf(epcFormat.get());
      } catch (IllegalArgumentException e) {
        throw new NotAcceptedException("Supported values for GS1-EPC-Format are: " + EPC_FORMATS);
      }
    }
    if (cbvFormat.isPresent()) {
      try {
        CBVFormat.valueOf(cbvFormat.get());
      } catch (IllegalArgumentException e) {
        throw new NotAcceptedException("Supported values for GS1-CBV-Format are: " + CBV_FORMATS);
      }
    }
  }

  public void isGs1CBVXmlFormatHeaderApplicable(Optional<String> cbvFormat, Optional<String> acceptHeader) {
    if (cbvFormat.isPresent() && (acceptHeader.isPresent() && !acceptHeader.get().toLowerCase().contains("/xml") && !acceptHeader.get().toLowerCase().contains("+xml"))) {
      throw new NotAcceptedException("GS1-CBV-XML-FORMAT header is applicable for application/xml contentType only.");
    }
  }

  public Optional<String> getCBVFormatValue(Optional<String> cbvFormat) {
    return cbvFormat.isPresent() ? cbvFormat : Optional.empty();
  }

  public static Set<String> getEventAccessRoles(Set<String> userRoles) {
    return userRoles.stream().filter(role -> (role.contains(Constants.EVENT_ACCESS) || role.equalsIgnoreCase(Constants.QUERY_ROLE))).collect(Collectors.toSet());
  }

  protected Map<String, String> getHeadersMap(String epcisVersion, Optional<String> epcisMin, Optional<String> epcisMax, Optional<String> cbvMin, Optional<String> cbvMax, Optional<String> cbvVersion) {
    Map<String, String> headers = null;
    if (epcisMax.isPresent() && cbvMax.isEmpty()) {
      headers = Map.of(CommonConstants.GS1_EPCIS_VERSION, epcisVersion, CommonConstants.GS1_EPCIS_MIN, epcisMin.get(), CommonConstants.GS1_EPCIS_MAX, epcisMax.get(), CommonConstants.GS1_CBV_VERSION, cbvVersion.get());
    } else if (cbvMax.isPresent() && epcisMax.isEmpty()) {
      headers = Map.of(CommonConstants.GS1_EPCIS_VERSION, epcisVersion, CommonConstants.GS1_CBV_MIN, cbvMin.get(), CommonConstants.GS1_CBV_MAX, cbvMax.get(), CommonConstants.GS1_CBV_VERSION, cbvVersion.get());
    } else if (cbvMin.isPresent() && epcisMin.isPresent()) {
      headers = Map.of(CommonConstants.GS1_EPCIS_VERSION, epcisVersion, CommonConstants.GS1_EPCIS_MIN, epcisMin.get(), CommonConstants.GS1_EPCIS_MAX, epcisMax.get(), CommonConstants.GS1_CBV_MIN, cbvMin.get(), CommonConstants.GS1_CBV_MAX, cbvMax.get(), CommonConstants.GS1_CBV_VERSION, cbvVersion.get());
    } else if (cbvVersion.isEmpty()) {
      headers = Map.of(CommonConstants.GS1_EPCIS_VERSION, epcisVersion);
    } else {
      headers = Map.of(CommonConstants.GS1_EPCIS_VERSION, epcisVersion, CommonConstants.GS1_CBV_VERSION, cbvVersion.get());
    }
    return headers;
  }

  public String getNextPageLinkHeader() {
    final Optional<String> requestURL = getRequestURL();
    return requestURL.map(u -> u.indexOf('?') >= 0 ? u.substring(0, u.lastIndexOf('?')) : u).orElse(StringUtils.EMPTY);
  }

  public String createLinkHeader(final String uri, final String rel) {
    return "<" + uri + ">; rel=\"" + rel + "\"";
  }

  public Response.ResponseBuilder applyEPCISContext(final EPCISContext ctx, final Response.ResponseBuilder builder) {
    if (ctx.getEpcisVersion() != null) {
      builder.header(HeaderConstants.GS1_EPCIS_VERSION, ctx.getEpcisVersion().getVersion());
    }
    if (ctx.getEpcisVersionMin() != null) {
      builder.header(HeaderConstants.GS1_EPCIS_MIN, ctx.getEpcisVersionMin().getVersion());
    }
    if (ctx.getEpcisVersionMax() != null) {
      builder.header(HeaderConstants.GS1_EPCIS_MAX, ctx.getEpcisVersionMax().getVersion());
    }
    if (ctx.getCbvVersion() != null) {
      builder.header(HeaderConstants.GS1_CBV_VERSION, ctx.getCbvVersion().getVersion());
    }
    if (ctx.getCbvVersionMin() != null) {
      builder.header(HeaderConstants.GS1_CBV_MIN, ctx.getCbvVersionMin().getVersion());
    }
    if (ctx.getCbvVersionMax() != null) {
      builder.header(HeaderConstants.GS1_CBV_MAX, ctx.getCbvVersionMax().getVersion());
    }
    if (ctx.getFormatPreference() != null) {
      builder.header(HeaderConstants.GS1_EPC_FORMAT, ctx.getFormatPreference().getEpcFormat().name());
      if (ctx.getEpcisFormat().equals(EPCISFormat.XML)) {
        builder.header(HeaderConstants.GS1_CBV_XML_FORMAT, ctx.getFormatPreference().getCbvFormat().name());
      }
    }
    if (ctx.getGs1Extensions().get().isPresent()) {
      builder.header(HeaderConstants.GS1_EXTENSIONS, ctx.getGs1Extensions().get().get());
    }
    return builder;
  }

  public Response.ResponseBuilder linkNextPage(final PaginationSupport paginationSupport, EPCISContext ctx, final Response.ResponseBuilder builder) {
    return linkNextPage(paginationSupport, applyEPCISContext(ctx, builder));
  }

  public Response.ResponseBuilder linkNextPage(final PaginationSupport paginationSupport, final Response.ResponseBuilder builder) {
    if (StringUtils.isNotEmpty(paginationSupport.getRel()) && paginationSupport.getRel().equals(NEXT)) {
      builder.header(LINK, createLinkHeader(getNextPageLinkHeader().concat(String.format(FORMAT_NEXT_PAGE_TOKEN, paginationSupport.getNextPageToken())), NEXT));
      builder.header(GS1_NEXT_PAGE_TOKEN_EXPIRES, paginationSupport.getTokenExpiryTime().toString());
    }
    return builder;
  }

  public Optional<EPCISVersion> epcisVersion() {
    return EPCISVersion.fromString(request.getHeader(HeaderConstants.GS1_EPCIS_VERSION));
  }

  public Optional<CBVVersion> cbvVersion() {
    return CBVVersion.fromString(request.getHeader(HeaderConstants.GS1_CBV_VERSION));
  }

  public String getSupportedGS1Extensions() {
    return userExtensionSchemaCache.getUserExtensionSchema().stream().map(userExtensionSchema -> userExtensionSchema.getDefaultPrefix().concat("=").concat(userExtensionSchema.getNamespace())).collect(Collectors.joining(","));
  }

  public Optional<EPCISFormat> epcisFormat() {
    return EPCISFormat.fromString(getContentType().orElse(""));
  }

  public Object getRequiredValueType(String paramName, List<String> valueList) {
    if (StringUtils.contains(paramName, "perPage") || StringUtils.contains(paramName, "nextPageToken") || StringUtils.contains(paramName, "INNER") || StringUtils.contains(paramName, "ILMD") || StringUtils.defaultString(paramName).matches("^(EXISTS|LE|EQ|LT|GT|GE)_[a-z][a-zA-Z0-9]*\\:[a-zA-Z0-9\\.\\:]+$") || StringUtils.defaultString(paramName).matches("^(EQ|EQ_INNER)_(readPoint|bizLocation)_[a-z][a-zA-Z0-9]*\\:\\w+$")) {
      return valueList;
    } else if (StringUtils.defaultString(paramName).matches("^(EQ)_(bizTransaction|destination|source)_[a-z][a-zA-Z0-9]*$")) {
      return valueList;
    } else if (StringUtils.defaultString(paramName).matches("^(LE|EQ|LT|GT|GE)_(value|minValue|maxValue|meanValue|quantity)_[A-Z0-9]{2,4}$") || StringUtils.defaultString(paramName).matches("^(EXISTS|LE|EQ|LT|GT|GE)_(SENSORELEMENT|SENSORMETADATA|SENSORREPORT)_[a-z][a-zA-Z0-9]*\\:\\w+$")) {
      return valueList;
    } else if (valueList.size() == 1 && valueList.toString().contains("|")) {
      return valueList.get(0);
    }
    ValueType valueType = QueryParamName.valueOf(paramName).getValueType();
    return switch (valueType) {
      case LONG, STRING, DATE_TIME, FLOAT, BOOLEAN -> valueList.get(0);
      case STRING_LIST, PAIR -> valueList;
    };
  }

  public SubscriptionContext subscriptionContext() {
    return this.subscriptionContext(securityIdentity);
  }

  public SubscriptionContext subscriptionContext(SecurityIdentity securityIdentity) {
    try {
      return new DefaultSubscriptionContext(getUserId(securityIdentity), RestResourceUtil.getUserInfo(userDetailsExtractor.getUserId(securityIdentity)), RestResourceUtil.getDefault(userDetailsExtractor.getDefaultGroup(securityIdentity)));
    } catch (RuntimeException e) {
      throw new QueryParameterException(e.getMessage(), e);
    }
  }

  private static String getDefault(String defaultGroup) {
    return defaultGroup;
  }

  private static String getUserInfo(String userId) {
    return userId;
  }

  public EPCPageResult createTopLevelResourcePageResult() {
    final EPCPageResult response = new EPCPageResult();
    response.setContextInfo(List.of(CommonConstants.EPCIS_DEFAULT_NAMESPACE));
    response.setType(COLLECTION);
    return response;
  }

  public Response buildResponse(QueryResult response, QueryContext ctx, String supportGS1Extensions, String fieldName) {
    final EPCISQueryDocument queryDocument = new EPCISQueryDocument(new EPCISQueryBody(response.getQueryResults()), Optional.of(ctx.getEpcisVersion().getVersion()));
    QueryDocumentUtil.updateContextOfQueryDocument(queryDocument, ctx.getGs1Extensions().get());
    final List<String> member = extractMember(response, fieldName);
    final TopLevelResource topLevelResource = new TopLevelResource(fieldName, member, queryDocument.getContext());
    final Response.ResponseBuilder builder = Response.ok(topLevelResource);
    builder.header(HeaderConstants.GS1_EXTENSIONS, supportGS1Extensions);
    return linkNextPage(response, ctx, builder).build();
  }

  private List<String> extractMember(QueryResult response, String fieldName) {
    final List<? extends EPCISEvent> events = response.getQueryResults().getResultsBody().getEventList();
    if (events.isEmpty()) {
      return Collections.emptyList();
    }
    switch (fieldName) {
    case "bizStep": 
      return events.stream().map(EPCISEvent::getBizStep).filter(Objects::nonNull).toList();
    case "bizLocation": 
      return events.stream().filter(event -> event.getBizLocation() != null).map(event -> event.getBizLocation().getId().toString()).toList();
    case "readPoint": 
      return events.stream().filter(event -> event.getReadPoint() != null).map(event -> event.getReadPoint().getId().toString()).toList();
    case "disposition": 
      return events.stream().map(EPCISEvent::getDisposition).filter(Objects::nonNull).toList();
    default: 
      throw new IllegalArgumentException("Invalid field name: " + fieldName);
    }
  }

  public RestResourceUtil(ApplicationConfigService appConfigService, CaptureEventManagementService captureEventManagementService, UserExtensionSchemaCache userExtensionSchemaCache, UserDetailsExtractor userDetailsExtractor) {
    this.appConfigService = appConfigService;
    this.captureEventManagementService = captureEventManagementService;
    this.userExtensionSchemaCache = userExtensionSchemaCache;
    this.userDetailsExtractor = userDetailsExtractor;
  }
}
