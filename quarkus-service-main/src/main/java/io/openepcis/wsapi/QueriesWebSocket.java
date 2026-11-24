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
package io.openepcis.wsapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.config.ApplicationConfigService;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.resource.RestResourceUtil;
import io.openepcis.service.api.SubscriptionService;
import io.openepcis.subscription.WebSocketRecord;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.ws.rs.core.Context;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Channel;

/**
 * Streaming query subscriptions over WebSocket ({@code GET /queries/{name}/events?stream=true}).
 *
 * <p>A socket is authenticated once, at the HTTP upgrade (by whatever mechanism the edition
 * has), and is then kept open regardless
 * of the token's {@code exp}. That is deliberate: a stream that died with every access token would
 * break every few minutes for every client, and the events it delivers are scoped to the
 * subscriber's group at subscription time anyway. The price: withdrawing a user's access reaches
 * an open stream only when that stream reconnects. Should that become unacceptable, the change is
 * to close with a code of its own (4401) at {@code exp} and let clients reconnect with a fresh
 * token, not to let the socket fail anonymously.
 */
@ServerEndpoint(value = "/queries/*", configurator = QueriesWebSocketConfigurator.class)
@ApplicationScoped
public class QueriesWebSocket {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueriesWebSocket.class);
	private static final Duration UNSUBSCRIBE_ON_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
	private final SubscriptionService subscriptionService;
	private final ManagedExecutor managedExecutor;
	private final RestResourceUtil restResourceUtil;
	private final ApplicationConfigService applicationConfigService;
	@Context
	SecurityIdentity securityIdentity;
	Map<String, Session> sessions = new ConcurrentHashMap<>();
	@Inject
	// every result of every pod arrives here (a consumer group per process); only those for a
	// session this pod holds are sent on, see WebSocketDelivery
	@Channel("websocket-subscription")
	Multi<String> websocketMessages;
	private static final ObjectMapper RECORD_READER = new ObjectMapper();
	/**
	 * How often every open socket is pinged. A streaming query is quiet for as long as nothing
	 * matching is captured, and the proxies in front of the repository end a quiet stream: Envoy
	 * after five minutes without a byte (its stream idle timeout), nginx after its read timeout.
	 * The client saw an abnormal close (1006) it could not tell from a refused token. A ping is
	 * traffic, so the stream stays up; browsers and the JDK client answer it on their own. Zero
	 * turns it off.
	 */
	@ConfigProperty(name = "openepcis.websocket.ping-interval", defaultValue = "PT30S")
	Duration pingInterval;
	private ScheduledExecutorService pinger;

	@PostConstruct
	public void postConstruct() {
		websocketMessages.map(QueriesWebSocket::readRecord).filter(r -> r != null && sessions.containsKey(r.sessionID())).subscribe().with(r -> {
			sendMessage(r.sessionID(), r.result());
		}, failure -> log.error("WebSocket result stream ended", failure));
		if (pingInterval != null && !pingInterval.isZero() && !pingInterval.isNegative()) {
			pinger = Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "queries-websocket-ping");
				thread.setDaemon(true);
				return thread;
			});
			long millis = pingInterval.toMillis();
			pinger.scheduleAtFixedRate(this::pingOpenSessions, millis, millis, TimeUnit.MILLISECONDS);
		}
	}

	/** Pings every open socket once; a socket that cannot take it is left to its own close. */
	int pingOpenSessions() {
		int pinged = 0;
		for (Session session : sessions.values()) {
			try {
				if (session.isOpen()) {
					session.getAsyncRemote().sendPing(ByteBuffer.allocate(0));
					pinged++;
				}
			} catch (Exception e) {
				log.debug("could not ping wsSession {}: {}", session.getId(), e.getMessage());
			}
		}
		return pinged;
	}

	@OnOpen
	@RolesAllowed("query")
	public void onOpen(Session session, EndpointConfig endpointConfig) throws IOException {
		sessions.put(session.getId(), session);
		String epcFormat = "always_gs1_digital_link";
		String cbvFormat = "no_preference";
		// the version of the documents this stream delivers, asked for by the client that opens it
		String epcisVersion = io.openepcis.resource.ParameterConstants.DEFAULT_EPCIS_VERSION_PARAMETER_VALUE;
		try {
			Map<String, List<String>> headers = QueriesWebSocketConfigurator.getHeaders();
			if (CollectionUtils.isNotEmpty(headers.get("GS1-EPC-Format"))) {
				epcFormat = headers.get("GS1-EPC-Format").get(0);
			}
			if (CollectionUtils.isNotEmpty(headers.get("GS1-CBV-Format"))) {
				cbvFormat = headers.get("GS1-CBV-Format").get(0);
			}
			if (CollectionUtils.isNotEmpty(headers.get("GS1-EPCIS-Version"))) {
				epcisVersion = headers.get("GS1-EPCIS-Version").get(0);
			}
			QueriesWebSocketConfigurator.clearHeaders();
			if (null != session.getRequestURI()) {
				var path = session.getRequestURI().getPath();
				var queryName = path.substring(0, path.lastIndexOf("/")).substring(path.substring(0, path.lastIndexOf("/")).lastIndexOf("/") + 1).trim();
				String subscriptionControls = StringUtils.isNotBlank(session.getRequestURI().getQuery()) ? session.getQueryString() : "stream=true";
				subscriptionService.subscribeViaWebSocket(queryName, subscriptionControls, session.getId(), FormatPreference.getInstance(EPCFormat.fromString(epcFormat).orElse(EPCFormat.No_Preference), CBVFormat.fromString(cbvFormat).orElse(CBVFormat.No_Preference)), restResourceUtil.subscriptionContext(securityIdentity), epcisVersion).runSubscriptionOn(managedExecutor).subscribe().with(s -> {
					log.info("queryName={} is subscribed with sessionID={}", queryName, session.getId());
				}, throwable -> {
					try {
						session.close(new CloseReason(CloseReason.CloseCodes.PROTOCOL_ERROR, throwable.getMessage()));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			} else {
				session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "invalid URI"));
			}
		} catch (Exception exception) {
			session.close(new CloseReason(CloseReason.CloseCodes.PROTOCOL_ERROR, exception.getMessage()));
		}
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		log.info("unsubscribing sessionID={} closeReason={}", session.getId(), closeReason);
		subscriptionService.unsubscribeByWebSocketSession(session.getId(), restResourceUtil.subscriptionContext(securityIdentity)).runSubscriptionOn(managedExecutor).subscribe().with(r -> {
			if (r) {
				log.info("unsubscribed for wsSession {}", session.getId());
			}
		}, throwable -> {
			log.error("failed to unsubscribe", throwable);
		});
		sessions.remove(session.getId(), session);
	}

	@OnError
	public void onError(Session session, Throwable error) throws IOException {
		log.error("unsubscribing sessionID= {} errorReason= {}", session.getId(), error.getMessage(), error);
		// A Uni does nothing until something subscribes to it. This call used to be made and
		// dropped, so a socket that ended in an error — a dropped network, a token that ran out
		// mid-stream, an idle timeout — left its subscription in the repository for ever. They
		// piled up: one named query on dev carried nineteen of them, one per reconnect over two
		// weeks, while onClose next door had always done this correctly.
		subscriptionService.unsubscribeByWebSocketSession(session.getId(), restResourceUtil.subscriptionContext(securityIdentity)).runSubscriptionOn(managedExecutor).subscribe().with(r -> {
			if (r) {
				log.info("unsubscribed for wsSession {}", session.getId());
			}
		}, throwable -> {
			log.error("failed to unsubscribe", throwable);
		});
		sessions.remove(session.getId(), session);
		if (error.getClass().equals(UnauthorizedException.class)) {
			session.close();
		}
	}

	@PreDestroy
	public void preDestroy() {
		if (pinger != null) {
			pinger.shutdownNow();
		}
		sessions.values().forEach(session -> {
			// Awaited rather than fired off: the process is going away, and a subscription left
			// behind here outlives it. Bounded, so a repository that will not answer cannot hold
			// up the shutdown.
			try {
				subscriptionService.unsubscribeByWebSocketSession(session.getId(), restResourceUtil.subscriptionContext(securityIdentity)).await().atMost(UNSUBSCRIBE_ON_SHUTDOWN_TIMEOUT);
			} catch (Exception e) {
				log.warn("could not unsubscribe wsSession {} while shutting down: {}", session.getId(), e.getMessage());
			}
			try {
				session.close();
			} catch (Exception e) {
				log.warn(e.getMessage());
			}
		});
	}

	static WebSocketRecord readRecord(String json) {
		try {
			return RECORD_READER.readValue(json, WebSocketRecord.class);
		} catch (Exception e) {
			log.warn("unreadable WebSocket result skipped: {}", e.getMessage());
			return null;
		}
	}

	public void sendMessage(String sessionID, String message) {
		try {
			final Session session = sessions.get(sessionID);
			if (session != null) {
				session.getAsyncRemote().sendText(message);
			}
		} catch (Exception e) {
			log.error(String.format("message delivery failed for sessionID=%s", sessionID), e);
		}
	}

	public QueriesWebSocket(SubscriptionService subscriptionService, ManagedExecutor managedExecutor, RestResourceUtil restResourceUtil, ApplicationConfigService applicationConfigService) {
		this.subscriptionService = subscriptionService;
		this.managedExecutor = managedExecutor;
		this.restResourceUtil = restResourceUtil;
		this.applicationConfigService = applicationConfigService;
	}
}
