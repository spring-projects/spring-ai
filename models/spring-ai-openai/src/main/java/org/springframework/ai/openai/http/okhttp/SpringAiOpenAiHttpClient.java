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

package org.springframework.ai.openai.http.okhttp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import com.openai.core.RequestOptions;
import com.openai.core.Timeout;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpMethod;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;
import com.openai.core.http.ProxyAuthenticator;
import com.openai.errors.OpenAIIoException;
import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpConnectionPoolMetrics;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationInterceptor;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.jspecify.annotations.Nullable;

/**
 * OkHttp-backed {@link HttpClient} for the OpenAI Java SDK, with Micrometer's
 * {@link OkHttpObservationInterceptor} attached so each HTTP attempt produces an
 * observation (span + metric + traceparent propagation). When a {@link MeterRegistry} is
 * supplied, {@link OkHttpConnectionPoolMetrics} is also bound. We own this code because
 * the SDK's stock {@code OpenAiOkHttpClient.Builder} doesn't expose an interceptor seam —
 * see the SDK's <a href="https://github.com/openai/openai-java#custom-http-client">Custom
 * HTTP client</a> guide for the integration pattern.
 *
 * <p>
 * <b>Attribution:</b> the body of this class is a Java port of {@code OkHttpClient} from
 * {@code openai-java-client-okhttp} (Apache License 2.0, authored by OpenAI). Only the
 * Micrometer wiring in {@link Builder#build()} is original to Spring AI.
 *
 * @author Soby Chacko
 * @author Ilayaperumal Gopinathan
 * @author Thomas Vitale
 * @since 2.0.0
 */
public final class SpringAiOpenAiHttpClient implements HttpClient {

	private final OkHttpClient okHttpClient;

	private final boolean ownsDispatcherExecutor;

	private SpringAiOpenAiHttpClient(OkHttpClient okHttpClient, boolean ownsDispatcherExecutor) {
		this.okHttpClient = okHttpClient;
		this.ownsDispatcherExecutor = ownsDispatcherExecutor;
	}

	public OkHttpClient getOkHttpClient() {
		return this.okHttpClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
		Call call = newCall(request, requestOptions);
		try {
			return toHttpResponse(call.execute());
		}
		catch (IOException e) {
			throw new OpenAIIoException("Request failed", e);
		}
		finally {
			HttpRequestBody body = request.body();
			if (body != null) {
				body.close();
			}
		}
	}

	@Override
	public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
		CompletableFuture<HttpResponse> future = new CompletableFuture<>();

		Call call = newCall(request, requestOptions);
		call.enqueue(new Callback() {
			@Override
			public void onResponse(Call call, Response response) {
				future.complete(toHttpResponse(response));
			}

			@Override
			public void onFailure(Call call, IOException e) {
				future.completeExceptionally(new OpenAIIoException("Request failed", e));
			}
		});

		future.whenComplete((unused, error) -> {
			if (error instanceof CancellationException) {
				call.cancel();
			}
			HttpRequestBody body = request.body();
			if (body != null) {
				body.close();
			}
		});

		return future;
	}

	@Override
	public void close() {
		if (this.ownsDispatcherExecutor) {
			this.okHttpClient.dispatcher().executorService().shutdown();
		}
		this.okHttpClient.connectionPool().evictAll();
		if (this.okHttpClient.cache() != null) {
			try {
				this.okHttpClient.cache().close();
			}
			catch (IOException ignored) {
				// Matches SDK behavior: cache close errors during shutdown are swallowed.
			}
		}
	}

	private Call newCall(HttpRequest request, RequestOptions requestOptions) {
		OkHttpClient.Builder clientBuilder = this.okHttpClient.newBuilder();

		Timeout perCallTimeout = requestOptions.getTimeout();
		if (perCallTimeout != null) {
			clientBuilder.connectTimeout(perCallTimeout.connect())
				.readTimeout(perCallTimeout.read())
				.writeTimeout(perCallTimeout.write())
				.callTimeout(perCallTimeout.request());
		}

		OkHttpClient client = clientBuilder.build();
		return client.newCall(toRequestWithStainlessHeaders(request, client));
	}

	private static Request toRequestWithStainlessHeaders(HttpRequest request, OkHttpClient client) {
		RequestBody body = toOkHttpRequestBody(request.body());
		if (body == null && requiresBody(request.method())) {
			body = RequestBody.create("", null);
		}

		Request.Builder builder = new Request.Builder().url(request.url()).method(request.method().name(), body);

		Headers headers = request.headers();
		for (String name : headers.names()) {
			for (String value : headers.values(name)) {
				builder.addHeader(name, value);
			}
		}

		if (!headers.names().contains("X-Stainless-Read-Timeout") && client.readTimeoutMillis() != 0) {
			builder.addHeader("X-Stainless-Read-Timeout",
					Long.toString(Duration.ofMillis(client.readTimeoutMillis()).getSeconds()));
		}
		if (!headers.names().contains("X-Stainless-Timeout") && client.callTimeoutMillis() != 0) {
			builder.addHeader("X-Stainless-Timeout",
					Long.toString(Duration.ofMillis(client.callTimeoutMillis()).getSeconds()));
		}

		return builder.build();
	}

	// URL is computed from scratch; client may be null (proxy-authenticator path).
	private static Request toRequestComputeUrl(HttpRequest request, @Nullable OkHttpClient client) {
		RequestBody body = toOkHttpRequestBody(request.body());
		if (body == null && requiresBody(request.method())) {
			body = RequestBody.create("", null);
		}

		Request.Builder builder = new Request.Builder().url(request.url()).method(request.method().name(), body);

		Headers headers = request.headers();
		for (String name : headers.names()) {
			for (String value : headers.values(name)) {
				builder.addHeader(name, value);
			}
		}

		if (client != null) {
			if (!headers.names().contains("X-Stainless-Read-Timeout") && client.readTimeoutMillis() != 0) {
				builder.addHeader("X-Stainless-Read-Timeout",
						Long.toString(Duration.ofMillis(client.readTimeoutMillis()).getSeconds()));
			}
			if (!headers.names().contains("X-Stainless-Timeout") && client.callTimeoutMillis() != 0) {
				builder.addHeader("X-Stainless-Timeout",
						Long.toString(Duration.ofMillis(client.callTimeoutMillis()).getSeconds()));
			}
		}

		return builder.build();
	}

	private static boolean requiresBody(HttpMethod method) {
		return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
	}

	private static @Nullable RequestBody toOkHttpRequestBody(@Nullable HttpRequestBody source) {
		if (source == null) {
			return null;
		}
		final String mediaTypeString = source.contentType();
		final MediaType mediaType = mediaTypeString != null ? MediaType.parse(mediaTypeString) : null;
		final long length = source.contentLength();

		return new RequestBody() {
			@Override
			public @Nullable MediaType contentType() {
				return mediaType;
			}

			@Override
			public long contentLength() {
				return length;
			}

			@Override
			public boolean isOneShot() {
				return !source.repeatable();
			}

			@Override
			public void writeTo(BufferedSink sink) throws IOException {
				source.writeTo(sink.outputStream());
			}
		};
	}

	private static HttpResponse toHttpResponse(Response response) {
		final Headers headers = toOpenAiHeaders(response.headers());
		return new HttpResponse() {
			@Override
			public int statusCode() {
				return response.code();
			}

			@Override
			public Headers headers() {
				return headers;
			}

			@Override
			public InputStream body() {
				return Objects.requireNonNull(response.body()).byteStream();
			}

			@Override
			public void close() {
				Objects.requireNonNull(response.body()).close();
			}
		};
	}

	private static HttpRequest toHttpRequest(Request request) {
		HttpRequest.Builder builder = HttpRequest.builder()
			.method(HttpMethod.valueOf(request.method()))
			.baseUrl(toBaseUrl(request.url()));
		for (String segment : request.url().pathSegments()) {
			builder.addPathSegment(segment);
		}
		for (String name : request.url().queryParameterNames()) {
			for (String value : request.url().queryParameterValues(name)) {
				if (value != null) {
					builder.putQueryParam(name, value);
				}
			}
		}
		okhttp3.Headers reqHeaders = request.headers();
		for (int i = 0, n = reqHeaders.size(); i < n; i++) {
			builder.putHeader(reqHeaders.name(i), reqHeaders.value(i));
		}
		RequestBody body = request.body();
		if (body != null) {
			builder.body(toHttpRequestBody(body));
		}
		return builder.build();
	}

	private static String toBaseUrl(HttpUrl url) {
		StringBuilder sb = new StringBuilder();
		sb.append(url.scheme()).append("://").append(url.host());
		if (url.port() != HttpUrl.defaultPort(url.scheme())) {
			sb.append(':').append(url.port());
		}
		return sb.toString();
	}

	private static HttpRequestBody toHttpRequestBody(RequestBody source) {
		final MediaType mediaType = source.contentType();
		final String mediaTypeString = mediaType != null ? mediaType.toString() : null;
		final long length;
		try {
			length = source.contentLength();
		}
		catch (IOException e) {
			throw new OpenAIIoException("Could not read content length", e);
		}
		final boolean isOneShot = source.isOneShot();

		return new HttpRequestBody() {
			@Override
			public @Nullable String contentType() {
				return mediaTypeString;
			}

			@Override
			public long contentLength() {
				return length;
			}

			@Override
			public boolean repeatable() {
				return !isOneShot;
			}

			@Override
			public void writeTo(OutputStream outputStream) {
				BufferedSink sink = Okio.buffer(Okio.sink(outputStream));
				try {
					source.writeTo(sink);
					sink.flush();
				}
				catch (IOException e) {
					throw new OpenAIIoException("Failed to write request body", e);
				}
			}

			@Override
			public void close() {
			}
		};
	}

	private static Headers toOpenAiHeaders(okhttp3.Headers okHttpHeaders) {
		Headers.Builder builder = Headers.builder();
		for (int i = 0, n = okHttpHeaders.size(); i < n; i++) {
			builder.put(okHttpHeaders.name(i), okHttpHeaders.value(i));
		}
		return builder.build();
	}

	/**
	 * Builder for {@link SpringAiOpenAiHttpClient}. Mirrors the upstream
	 * {@code com.openai.client.okhttp.OkHttpClient.Builder} surface, with two additional
	 * optional inputs: an {@link ObservationRegistry} (defaults to
	 * {@link ObservationRegistry#NOOP}) and a {@link MeterRegistry} (optional; when
	 * supplied, OkHttp connection-pool gauges are bound to it).
	 */
	public static final class Builder {

		private static final String OBSERVATION_NAME = "okhttp.requests";

		private Timeout timeout = Timeout.builder().build();

		private @Nullable Proxy proxy;

		private @Nullable ProxyAuthenticator proxyAuthenticator;

		private @Nullable Integer maxIdleConnections;

		private @Nullable Duration keepAliveDuration;

		private @Nullable ExecutorService dispatcherExecutorService;

		private @Nullable SSLSocketFactory sslSocketFactory;

		private @Nullable X509TrustManager trustManager;

		private @Nullable HostnameVerifier hostnameVerifier;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private @Nullable MeterRegistry meterRegistry;

		private Iterable<Tag> meterTags = Collections.emptyList();

		private final List<Interceptor> interceptors = new ArrayList<>();

		private Builder() {
		}

		public Builder timeout(Timeout timeout) {
			this.timeout = timeout;
			return this;
		}

		public Builder timeout(Duration timeout) {
			return timeout(Timeout.builder().request(timeout).build());
		}

		public Builder proxy(@Nullable Proxy proxy) {
			this.proxy = proxy;
			return this;
		}

		public Builder proxyAuthenticator(@Nullable ProxyAuthenticator proxyAuthenticator) {
			this.proxyAuthenticator = proxyAuthenticator;
			return this;
		}

		public Builder maxIdleConnections(@Nullable Integer maxIdleConnections) {
			this.maxIdleConnections = maxIdleConnections;
			return this;
		}

		public Builder keepAliveDuration(@Nullable Duration keepAliveDuration) {
			this.keepAliveDuration = keepAliveDuration;
			return this;
		}

		/**
		 * Sets the executor used by the OkHttp dispatcher. When supplied, the caller owns
		 * the executor's lifecycle — {@link SpringAiOpenAiHttpClient#close()} will not
		 * shut it down. When null, an internal default is created and closed with the
		 * client.
		 */
		public Builder dispatcherExecutorService(@Nullable ExecutorService dispatcherExecutorService) {
			this.dispatcherExecutorService = dispatcherExecutorService;
			return this;
		}

		public Builder sslSocketFactory(@Nullable SSLSocketFactory sslSocketFactory) {
			this.sslSocketFactory = sslSocketFactory;
			return this;
		}

		public Builder trustManager(@Nullable X509TrustManager trustManager) {
			this.trustManager = trustManager;
			return this;
		}

		public Builder hostnameVerifier(@Nullable HostnameVerifier hostnameVerifier) {
			this.hostnameVerifier = hostnameVerifier;
			return this;
		}

		/**
		 * Registers the Micrometer observation registry used by the OkHttp interceptor.
		 * Defaults to {@link ObservationRegistry#NOOP}, which makes the interceptor a
		 * no-op — no metric/span overhead in environments that don't wire observability.
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry");
			return this;
		}

		/**
		 * Registers a meter registry to bind OkHttp connection-pool gauges (active/idle
		 * connections). Optional; when omitted, no pool gauges are registered.
		 */
		public Builder meterRegistry(@Nullable MeterRegistry meterRegistry) {
			this.meterRegistry = meterRegistry;
			return this;
		}

		/**
		 * Tags applied to the connection-pool gauges registered with the
		 * {@link MeterRegistry}. Useful for distinguishing multiple clients writing pool
		 * metrics into the same registry (e.g. a sync and an async client sharing a
		 * registry). Has no effect when no meter registry is set.
		 */
		public Builder meterTags(Iterable<Tag> meterTags) {
			this.meterTags = Objects.requireNonNull(meterTags, "meterTags");
			return this;
		}

		/**
		 * Registers an OkHttp {@link Interceptor} to run at the end of the chain.
		 * Interceptors appear in the chain in the order they are added here. Use this to
		 * attach cross-cutting concerns such as OAuth2 bearer-token injection, custom
		 * logging, or tenant-propagation headers.
		 */
		public Builder interceptor(Interceptor interceptor) {
			this.interceptors.add(Objects.requireNonNull(interceptor, "interceptor"));
			return this;
		}

		public SpringAiOpenAiHttpClient build() {
			OkHttpClient.Builder okBuilder = new OkHttpClient.Builder()
				// SDK's RetryingHttpClient owns retries; disable here to avoid doubling.
				.retryOnConnectionFailure(false)
				.pingInterval(Duration.ofMinutes(1))
				.connectTimeout(this.timeout.connect())
				.readTimeout(this.timeout.read())
				.writeTimeout(this.timeout.write())
				.callTimeout(this.timeout.request())
				.proxy(this.proxy);

			OkHttpObservationInterceptor observationInterceptor = OkHttpObservationInterceptor
				.builder(this.observationRegistry, OBSERVATION_NAME)
				.build();
			okBuilder.addInterceptor(observationInterceptor);

			for (Interceptor interceptor : this.interceptors) {
				okBuilder.addInterceptor(interceptor);
			}

			if (this.proxyAuthenticator != null) {
				final ProxyAuthenticator pa = this.proxyAuthenticator;
				okBuilder.proxyAuthenticator((route, response) -> {
					Proxy routeProxy = route != null ? route.proxy() : Proxy.NO_PROXY;
					Optional<HttpRequest> authed = pa.authenticate(routeProxy, toHttpRequest(response.request()),
							toHttpResponse(response));
					return authed.map(req -> toRequestComputeUrl(req, null)).orElse(null);
				});
			}

			ExecutorService userDispatcherExecutor = this.dispatcherExecutorService;
			boolean ownsDispatcherExecutor = userDispatcherExecutor == null;
			ExecutorService dispatcherBase = (userDispatcherExecutor != null) ? userDispatcherExecutor
					: defaultDispatcherExecutor();
			ExecutorService dispatcherExecutor = ContextExecutorService.wrap(dispatcherBase,
					ContextSnapshotFactory.builder().build());
			okBuilder.dispatcher(new Dispatcher(dispatcherExecutor));

			if (this.maxIdleConnections != null && this.keepAliveDuration != null) {
				okBuilder.connectionPool(new ConnectionPool(this.maxIdleConnections, this.keepAliveDuration.toNanos(),
						TimeUnit.NANOSECONDS));
			}
			else if ((this.maxIdleConnections == null) != (this.keepAliveDuration == null)) {
				throw new IllegalStateException(
						"Both or none of `maxIdleConnections` and `keepAliveDuration` must be set, but only one was set");
			}

			if (this.sslSocketFactory != null && this.trustManager != null) {
				okBuilder.sslSocketFactory(this.sslSocketFactory, this.trustManager);
			}
			else if ((this.sslSocketFactory == null) != (this.trustManager == null)) {
				throw new IllegalStateException(
						"Both or none of `sslSocketFactory` and `trustManager` must be set, but only one was set");
			}

			if (this.hostnameVerifier != null) {
				okBuilder.hostnameVerifier(this.hostnameVerifier);
			}

			OkHttpClient okClient = okBuilder.build();
			// Same-host traffic: raise per-host limit to overall request limit. Matches
			// the SDK's tuning at the bottom of `OkHttpClient.Builder.build()`.
			okClient.dispatcher().setMaxRequestsPerHost(okClient.dispatcher().getMaxRequests());

			if (this.meterRegistry != null) {
				new OkHttpConnectionPoolMetrics(okClient.connectionPool(), this.meterTags).bindTo(this.meterRegistry);
			}

			return new SpringAiOpenAiHttpClient(okClient, ownsDispatcherExecutor);
		}

		// Replicates OkHttp's default dispatcher so wrapping for context propagation
		// preserves the standard "OkHttp Dispatcher-N" thread names.
		private static ExecutorService defaultDispatcherExecutor() {
			AtomicInteger counter = new AtomicInteger(1);
			ThreadFactory threadFactory = runnable -> {
				Thread thread = new Thread(runnable, "OkHttp Dispatcher-" + counter.getAndIncrement());
				thread.setDaemon(false);
				return thread;
			};
			return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
					threadFactory);
		}

	}

}
