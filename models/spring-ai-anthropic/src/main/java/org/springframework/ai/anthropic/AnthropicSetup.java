/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.anthropic;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.anthropic.backends.AnthropicBackend;
import com.anthropic.backends.Backend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.AnthropicClientAsyncImpl;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.core.ClientOptions;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.anthropic.http.okhttp.SpringAiAnthropicHttpClient;
import org.springframework.util.Assert;

/**
 * Factory class for creating and configuring Anthropic SDK client instances.
 *
 * <p>
 * This utility class provides static factory methods for creating both synchronous
 * ({@link AnthropicClient}) and asynchronous ({@link AnthropicClientAsync}) clients with
 * comprehensive configuration support. It handles API key detection from environment
 * variables and provides sensible defaults for timeouts and retry behavior.
 *
 * <p>
 * <b>Client Types:</b>
 * <ul>
 * <li><b>Synchronous Client:</b> Used for blocking API calls via
 * {@link #setupSyncClient}</li>
 * <li><b>Asynchronous Client:</b> Used for streaming responses via
 * {@link #setupAsyncClient}</li>
 * </ul>
 *
 * <p>
 * <b>Environment Variable Support:</b>
 * <ul>
 * <li>{@code ANTHROPIC_API_KEY} - Primary API key for authentication</li>
 * <li>{@code ANTHROPIC_AUTH_TOKEN} - Alternative authentication token</li>
 * <li>{@code ANTHROPIC_BASE_URL} - Override the default API endpoint</li>
 * </ul>
 *
 * <p>
 * <b>Default Configuration:</b>
 * <ul>
 * <li>Timeout: 60 seconds</li>
 * <li>Max Retries: 2</li>
 * <li>User-Agent: {@code spring-ai-anthropic-sdk}</li>
 * </ul>
 *
 * <p>
 * This class is not intended to be instantiated directly. Use the static factory methods
 * to create client instances.
 *
 * @author Soby Chacko
 * @since 2.0.0
 * @see org.springframework.ai.anthropic.AnthropicChatModel
 */
public final class AnthropicSetup {

	static final String ANTHROPIC_URL = "https://api.anthropic.com";

	static final String ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";

	static final String ANTHROPIC_AUTH_TOKEN = "ANTHROPIC_AUTH_TOKEN";

	static final String ANTHROPIC_BASE_URL = "ANTHROPIC_BASE_URL";

	static final String DEFAULT_USER_AGENT = "spring-ai-anthropic-sdk";

	private static final Log logger = LogFactory.getLog(AnthropicSetup.class);

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

	private static final int DEFAULT_MAX_RETRIES = 2;

	// Disambiguates sync/async client connection-pool gauges sharing one MeterRegistry.
	private static final List<Tag> SYNC_CLIENT_TAGS = List.of(Tag.of("client.kind", "sync"));

	private static final List<Tag> ASYNC_CLIENT_TAGS = List.of(Tag.of("client.kind", "async"));

	private AnthropicSetup() {
	}

	/**
	 * Creates a synchronous Anthropic client with the specified configuration. Delegates
	 * to
	 * {@link #setupSyncClient(String, String, Duration, Integer, Proxy, Map, ObservationRegistry, MeterRegistry)}
	 * with a {@link ObservationRegistry#NOOP no-op} observation registry and no meter
	 * registry — i.e. HTTP-layer observability is disabled.
	 * @param baseUrl the base URL for the API (null to use default or environment
	 * variable)
	 * @param apiKey the API key (null to detect from environment)
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @return a configured Anthropic client
	 */
	public static AnthropicClient setupSyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders) {
		return setupSyncClient(baseUrl, apiKey, timeout, maxRetries, proxy, customHeaders, ObservationRegistry.NOOP,
				null, null, List.of());
	}

	/**
	 * Creates a synchronous Anthropic client whose underlying OkHttp client is
	 * instrumented with Micrometer: each HTTP attempt emits an observation (span + metric
	 * + {@code traceparent} propagation), and, when a {@link MeterRegistry} is supplied,
	 * OkHttp connection-pool gauges are bound to it.
	 * @param baseUrl the base URL for the API (null to use default or environment
	 * variable)
	 * @param apiKey the API key (null to detect from environment)
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @param observationRegistry the registry the OkHttp observation interceptor reports
	 * to; pass {@link ObservationRegistry#NOOP} to disable
	 * @param meterRegistry optional; when supplied, OkHttp connection-pool gauges
	 * (active/idle connections) are registered
	 * @return a configured Anthropic client
	 * @since 2.0.0
	 */
	public static AnthropicClient setupSyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders, ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry) {
		return setupSyncClient(baseUrl, apiKey, timeout, maxRetries, proxy, customHeaders, observationRegistry,
				meterRegistry, null, List.of());
	}

	/**
	 * Creates a synchronous Anthropic client backed by a caller-supplied dispatcher
	 * executor (e.g. one built around {@code Executors.newVirtualThreadPerTaskExecutor()}
	 * on Java 21+). The caller owns the executor's lifecycle; closing the resulting
	 * client will not shut it down. See
	 * {@link #setupSyncClient(String, String, Duration, Integer, Proxy, Map, ObservationRegistry, MeterRegistry)}
	 * for the remaining parameter semantics.
	 * @param dispatcherExecutor the OkHttp dispatcher executor; null to use the
	 * library-managed default
	 * @return a configured Anthropic client
	 * @since 2.0.0
	 */
	public static AnthropicClient setupSyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders, ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry, @Nullable ExecutorService dispatcherExecutor) {
		return setupSyncClient(baseUrl, apiKey, timeout, maxRetries, proxy, customHeaders, observationRegistry,
				meterRegistry, dispatcherExecutor, List.of());
	}

	/**
	 * Creates a synchronous Anthropic client backed by a caller-supplied dispatcher
	 * executor and with optional HTTP client customizers. See
	 * {@link #setupSyncClient(String, String, Duration, Integer, Proxy, Map, ObservationRegistry, MeterRegistry, ExecutorService)}
	 * for the remaining parameter semantics.
	 * @param httpClientCustomizers customizers applied to the underlying OkHttp client
	 * builder after Spring AI's own defaults; useful for registering interceptors (e.g.
	 * OAuth2 bearer-token injection) or custom TLS configuration
	 * @return a configured Anthropic client
	 * @since 2.0.0
	 */
	public static AnthropicClient setupSyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders, ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry, @Nullable ExecutorService dispatcherExecutor,
			List<AnthropicHttpClientBuilderCustomizer> httpClientCustomizers) {

		Assert.notNull(httpClientCustomizers, "httpClientCustomizers must not be null");
		String resolvedBaseUrl = detectBaseUrlFromEnv(baseUrl);
		String resolvedApiKey = apiKey != null ? apiKey : detectApiKey();
		AnthropicBackend backend = buildAnthropicBackend(resolvedBaseUrl, resolvedApiKey);
		ClientOptions opts = buildClientOptions(backend, timeout, maxRetries, proxy, customHeaders, observationRegistry,
				meterRegistry, SYNC_CLIENT_TAGS, dispatcherExecutor, httpClientCustomizers);
		return new AnthropicClientImpl(opts);
	}

	/**
	 * Creates a synchronous Anthropic client with the specified SDK {@link Backend}. This
	 * overload supports alternative backends (e.g. Google Vertex AI) while preserving
	 * Spring AI's OkHttp observability and customizer pipeline.
	 * @param backend the SDK backend (e.g. {@link AnthropicBackend} or
	 * {@code VertexBackend})
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @param observationRegistry the registry the OkHttp observation interceptor reports
	 * to; pass {@link ObservationRegistry#NOOP} to disable
	 * @param meterRegistry optional; when supplied, OkHttp connection-pool gauges are
	 * registered
	 * @param dispatcherExecutor the OkHttp dispatcher executor; null to use the
	 * library-managed default
	 * @param httpClientCustomizers customizers applied to the underlying OkHttp client
	 * builder after Spring AI's own defaults
	 * @return a configured Anthropic client
	 * @since 2.0.0
	 */
	public static AnthropicClient setupSyncClient(Backend backend, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			ObservationRegistry observationRegistry, @Nullable MeterRegistry meterRegistry,
			@Nullable ExecutorService dispatcherExecutor,
			List<AnthropicHttpClientBuilderCustomizer> httpClientCustomizers) {

		Assert.notNull(backend, "backend must not be null");
		Assert.notNull(httpClientCustomizers, "httpClientCustomizers must not be null");
		ClientOptions opts = buildClientOptions(backend, timeout, maxRetries, proxy, customHeaders, observationRegistry,
				meterRegistry, SYNC_CLIENT_TAGS, dispatcherExecutor, httpClientCustomizers);
		return new AnthropicClientImpl(opts);
	}

	/**
	 * Creates an asynchronous Anthropic client with the specified configuration.
	 * Delegates to
	 * {@link #setupAsyncClient(String, String, Duration, Integer, Proxy, Map, ObservationRegistry, MeterRegistry)}
	 * with a {@link ObservationRegistry#NOOP no-op} observation registry and no meter
	 * registry — i.e. HTTP-layer observability is disabled.
	 * @param baseUrl the base URL for the API (null to use default or environment
	 * variable)
	 * @param apiKey the API key (null to detect from environment)
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @return a configured async Anthropic client
	 */
	public static AnthropicClientAsync setupAsyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders) {
		return setupAsyncClient(baseUrl, apiKey, timeout, maxRetries, proxy, customHeaders, ObservationRegistry.NOOP,
				null, null, List.of());
	}

	/**
	 * Creates an asynchronous Anthropic client whose underlying OkHttp client is
	 * instrumented with Micrometer. See
	 * {@link #setupSyncClient(String, String, Duration, Integer, Proxy, Map, ObservationRegistry, MeterRegistry)}
	 * for parameter semantics.
	 * @param baseUrl the base URL for the API (null to use default or environment
	 * variable)
	 * @param apiKey the API key (null to detect from environment)
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @param observationRegistry the registry the OkHttp observation interceptor reports
	 * to; pass {@link ObservationRegistry#NOOP} to disable
	 * @param meterRegistry optional; when supplied, OkHttp connection-pool gauges are
	 * registered
	 * @return a configured async Anthropic client
	 * @since 2.0.0
	 */
	public static AnthropicClientAsync setupAsyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders, ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry) {
		return setupAsyncClient(baseUrl, apiKey, timeout, maxRetries, proxy, customHeaders, observationRegistry,
				meterRegistry, null, List.of());
	}

	/**
	 * Creates an asynchronous Anthropic client backed by a caller-supplied dispatcher
	 * executor. The caller owns the executor's lifecycle; closing the resulting client
	 * will not shut it down. See
	 * {@link #setupSyncClient(String, String, Duration, Integer, Proxy, Map, ObservationRegistry, MeterRegistry, ExecutorService)}
	 * for the dispatcher executor's role.
	 * @param dispatcherExecutor the OkHttp dispatcher executor; null to use the
	 * library-managed default
	 * @return a configured async Anthropic client
	 * @since 2.0.0
	 */
	public static AnthropicClientAsync setupAsyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders, ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry, @Nullable ExecutorService dispatcherExecutor) {
		return setupAsyncClient(baseUrl, apiKey, timeout, maxRetries, proxy, customHeaders, observationRegistry,
				meterRegistry, dispatcherExecutor, List.of());
	}

	/**
	 * Creates an asynchronous Anthropic client backed by a caller-supplied dispatcher
	 * executor and with optional HTTP client customizers. See
	 * {@link #setupAsyncClient(String, String, Duration, Integer, Proxy, Map, ObservationRegistry, MeterRegistry, ExecutorService)}
	 * for the remaining parameter semantics.
	 * @param httpClientCustomizers customizers applied to the underlying OkHttp client
	 * builder after Spring AI's own defaults; useful for registering interceptors (e.g.
	 * OAuth2 bearer-token injection) or custom TLS configuration
	 * @return a configured async Anthropic client
	 * @since 2.0.0
	 */
	public static AnthropicClientAsync setupAsyncClient(@Nullable String baseUrl, @Nullable String apiKey,
			@Nullable Duration timeout, @Nullable Integer maxRetries, @Nullable Proxy proxy,
			@Nullable Map<String, String> customHeaders, ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry, @Nullable ExecutorService dispatcherExecutor,
			List<AnthropicHttpClientBuilderCustomizer> httpClientCustomizers) {

		Assert.notNull(httpClientCustomizers, "httpClientCustomizers must not be null");
		String resolvedBaseUrl = detectBaseUrlFromEnv(baseUrl);
		String resolvedApiKey = apiKey != null ? apiKey : detectApiKey();
		AnthropicBackend backend = buildAnthropicBackend(resolvedBaseUrl, resolvedApiKey);
		ClientOptions opts = buildClientOptions(backend, timeout, maxRetries, proxy, customHeaders, observationRegistry,
				meterRegistry, ASYNC_CLIENT_TAGS, dispatcherExecutor, httpClientCustomizers);
		return new AnthropicClientAsyncImpl(opts);
	}

	/**
	 * Creates an asynchronous Anthropic client with the specified SDK {@link Backend}.
	 * This overload supports alternative backends (e.g. Google Vertex AI) while
	 * preserving Spring AI's OkHttp observability and customizer pipeline.
	 * @param backend the SDK backend (e.g. {@link AnthropicBackend} or
	 * {@code VertexBackend})
	 * @param timeout the request timeout (null to use default of 60 seconds)
	 * @param maxRetries the maximum number of retries (null to use default of 2)
	 * @param proxy the proxy to use (null for no proxy)
	 * @param customHeaders additional HTTP headers to include in requests
	 * @param observationRegistry the registry the OkHttp observation interceptor reports
	 * to; pass {@link ObservationRegistry#NOOP} to disable
	 * @param meterRegistry optional; when supplied, OkHttp connection-pool gauges are
	 * registered
	 * @param dispatcherExecutor the OkHttp dispatcher executor; null to use the
	 * library-managed default
	 * @param httpClientCustomizers customizers applied to the underlying OkHttp client
	 * builder after Spring AI's own defaults
	 * @return a configured async Anthropic client
	 * @since 2.0.0
	 */
	public static AnthropicClientAsync setupAsyncClient(Backend backend, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			ObservationRegistry observationRegistry, @Nullable MeterRegistry meterRegistry,
			@Nullable ExecutorService dispatcherExecutor,
			List<AnthropicHttpClientBuilderCustomizer> httpClientCustomizers) {

		Assert.notNull(backend, "backend must not be null");
		Assert.notNull(httpClientCustomizers, "httpClientCustomizers must not be null");
		ClientOptions opts = buildClientOptions(backend, timeout, maxRetries, proxy, customHeaders, observationRegistry,
				meterRegistry, ASYNC_CLIENT_TAGS, dispatcherExecutor, httpClientCustomizers);
		return new AnthropicClientAsyncImpl(opts);
	}

	private static ClientOptions buildClientOptions(Backend backend, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			ObservationRegistry observationRegistry, @Nullable MeterRegistry meterRegistry,
			Iterable<Tag> connectionPoolTags, @Nullable ExecutorService dispatcherExecutor,
			List<AnthropicHttpClientBuilderCustomizer> httpClientCustomizers) {

		Duration resolvedTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
		int resolvedMaxRetries = maxRetries != null ? maxRetries : DEFAULT_MAX_RETRIES;

		ClientOptions.Builder optsBuilder = ClientOptions.builder()
			.timeout(resolvedTimeout)
			.maxRetries(resolvedMaxRetries)
			.putHeader("User-Agent", DEFAULT_USER_AGENT);
		if (customHeaders != null) {
			customHeaders.forEach(optsBuilder::putHeader);
		}

		SpringAiAnthropicHttpClient.Builder rawHttpBuilder = SpringAiAnthropicHttpClient.builder()
			.backend(backend)
			.timeout(resolvedTimeout)
			.proxy(proxy)
			.observationRegistry(observationRegistry)
			.meterRegistry(meterRegistry)
			.dispatcherExecutorService(dispatcherExecutor);

		for (AnthropicHttpClientBuilderCustomizer customizer : httpClientCustomizers) {
			customizer.customize(rawHttpBuilder);
		}

		// Re-apply Spring AI's resolved backend and meterTags after the customizer loop
		// so that neither can be inadvertently overridden:
		// - backend: applyCredentials() below relies on the same backend instance; a
		// replacement would misalign WIF credential injection.
		// - meterTags: SYNC_CLIENT_TAGS / ASYNC_CLIENT_TAGS discriminate the two OkHttp
		// connection-pool metric bindings; identical tags would cause duplicate gauge
		// registration in the MeterRegistry at startup.
		rawHttpBuilder.backend(backend);
		rawHttpBuilder.meterTags(connectionPoolTags);

		SpringAiAnthropicHttpClient rawHttp = rawHttpBuilder.build();

		// No-op when a static apiKey/authToken is set; otherwise fetches WIF credentials.
		// Only applicable to AnthropicBackend (direct API); VertexBackend and other
		// backends manage credentials independently.
		if (backend instanceof AnthropicBackend anthropicBackend) {
			anthropicBackend.applyCredentials(rawHttp, optsBuilder);
		}

		return optsBuilder.httpClient(rawHttp).build();
	}

	private static AnthropicBackend buildAnthropicBackend(@Nullable String baseUrl, @Nullable String apiKey) {
		AnthropicBackend.Builder b = AnthropicBackend.builder();
		if (baseUrl != null) {
			b.baseUrl(baseUrl);
		}
		if (apiKey != null) {
			b.apiKey(apiKey);
		}
		return b.build();
	}

	/**
	 * Detects the base URL from environment variable if not explicitly provided.
	 * @param baseUrl the explicitly provided base URL (may be null)
	 * @return the base URL to use
	 */
	static @Nullable String detectBaseUrlFromEnv(@Nullable String baseUrl) {
		if (baseUrl == null) {
			String envBaseUrl = System.getenv(ANTHROPIC_BASE_URL);
			if (envBaseUrl != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Anthropic Base URL detected from environment variable " + ANTHROPIC_BASE_URL + ".");
				}
				return envBaseUrl;
			}
		}
		return baseUrl;
	}

	/**
	 * Detects the API key from environment variables.
	 * @return the API key, or null if not found
	 */
	static @Nullable String detectApiKey() {
		String apiKey = System.getenv(ANTHROPIC_API_KEY);
		if (apiKey != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Anthropic API key detected from environment variable " + ANTHROPIC_API_KEY + ".");
			}
			return apiKey;
		}

		String authToken = System.getenv(ANTHROPIC_AUTH_TOKEN);
		if (authToken != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Anthropic auth token detected from environment variable " + ANTHROPIC_AUTH_TOKEN + ".");
			}
			return authToken;
		}

		return null;
	}

}
