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

package org.springframework.ai.bedrock.converse.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Fetches media content from HTTP/HTTPS URLs with SSRF and resource-exhaustion
 * protections.
 *
 * <p>
 * Protection measures:
 * <ul>
 * <li>Socket-level blocking via {@link SsrfBlockingPlainSocketFactory} and
 * {@link SsrfBlockingSSLSocketFactory}: the resolved {@link java.net.InetAddress} is
 * checked at {@code connectSocket()} time — after DNS resolution — so raw IP literals
 * (e.g. {@code 127.0.0.1}, {@code 169.254.169.254}) are blocked even when no DNS lookup
 * occurs.</li>
 * <li>DNS-level blocking via {@link SsrfSafeDnsResolver}: hostnames that resolve to
 * internal addresses are rejected early, before a connection attempt is made. This
 * provides a fast-fail path for hostname-based requests and limits DNS rebinding
 * exposure.</li>
 * <li>HTTP redirects are disabled to prevent redirect chains that lead to internal
 * addresses.</li>
 * <li>Connect and socket timeouts prevent slow-server resource exhaustion.</li>
 * <li>Response bodies are capped at {@value #DEFAULT_MAX_FETCH_SIZE_BYTES} bytes to
 * prevent memory exhaustion.</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public final class MediaFetcher {

	/**
	 * Maximum number of bytes fetched from a media URL. Protects against memory
	 * exhaustion when a user-supplied URL points to arbitrarily large content (40 MB).
	 */
	public static final int DEFAULT_MAX_FETCH_SIZE_BYTES = 40 * 1024 * 1024;

	/** Connect timeout for opening a connection to the media URL. */
	private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 15;

	/** Socket timeout for reading from the media URL connection. */
	private static final int DEFAULT_SOCKET_TIMEOUT_SECONDS = 30;

	private final RestClient restClient;

	/**
	 * Optional set of allowed hostnames. When non-empty, only hosts in this set (or
	 * matching a {@code *.suffix} wildcard entry) are permitted. An empty set means no
	 * allowlist is enforced and only the SSRF blocklist applies.
	 */
	private final Set<String> allowedHosts;

	/**
	 * Creates a {@code MediaFetcher} with no host allowlist (blocklist-only protection).
	 */
	public MediaFetcher() {
		this(Set.of());
	}

	/**
	 * Creates a {@code MediaFetcher} with an optional host allowlist.
	 *
	 * <p>
	 * When {@code allowedHosts} is non-empty, every fetch is checked against this set
	 * before the SSRF blocklist. A host is allowed when it either equals an entry exactly
	 * (case-insensitive) or matches a wildcard entry of the form {@code *.example.com}.
	 * @param allowedHosts set of permitted hostnames or wildcard patterns; an empty set
	 * disables allowlist enforcement
	 */
	public MediaFetcher(Set<String> allowedHosts) {
		this.allowedHosts = Set.copyOf(allowedHosts);
		this.restClient = createSsrfSafeRestClient();
	}

	/**
	 * Package-private constructor for testing — allows injecting a custom
	 * {@link RestClient} (e.g. one backed by {@code MockRestServiceServer}).
	 */
	MediaFetcher(Set<String> allowedHosts, RestClient restClient) {
		this.allowedHosts = Set.copyOf(allowedHosts);
		this.restClient = restClient;
	}

	/**
	 * Fetches the content at {@code uri} and returns it as a byte array.
	 *
	 * <p>
	 * The caller is responsible for validating the URI (protocol, host) before invoking
	 * this method. This method enforces size limits and socket-level SSRF protection.
	 * @param uri the URI to fetch
	 * @return the response body as a byte array
	 * @throws SecurityException if the response exceeds
	 * {@link #DEFAULT_MAX_FETCH_SIZE_BYTES} or the host resolves to a blocked internal
	 * address
	 * @throws org.springframework.web.client.RestClientException on HTTP or I/O errors
	 */
	public byte[] fetch(URI uri) {
		if (!this.allowedHosts.isEmpty()) {
			String host = uri.getHost();
			if (!isHostAllowed(host)) {
				throw new SecurityException("Host '" + host
						+ "' is not in the allowed hosts list. Configure MediaFetcher with the appropriate allowed hosts.");
			}
		}
		return this.restClient.get().uri(uri).exchange((request, response) -> {
			long contentLength = response.getHeaders().getContentLength();
			if (contentLength > DEFAULT_MAX_FETCH_SIZE_BYTES) {
				throw new SecurityException("Media URL response exceeds maximum allowed size of "
						+ DEFAULT_MAX_FETCH_SIZE_BYTES + " bytes: " + uri);
			}
			try (InputStream body = response.getBody()) {
				return readWithSizeLimit(body, DEFAULT_MAX_FETCH_SIZE_BYTES);
			}
		}, true);
	}

	/**
	 * Returns {@code true} if {@code host} is permitted by the allowlist. An entry that
	 * starts with {@code *.} is treated as a suffix wildcard matching any subdomain (e.g.
	 * {@code *.example.com} matches {@code img.example.com} but not {@code example.com}
	 * itself).
	 */
	private boolean isHostAllowed(String host) {
		if (host == null) {
			return false;
		}
		String normalizedHost = host.toLowerCase();
		for (String allowed : this.allowedHosts) {
			String normalizedAllowed = allowed.toLowerCase();
			if (normalizedAllowed.startsWith("*.")) {
				// wildcard: *.example.com → matches img.example.com
				String suffix = normalizedAllowed.substring(1); // ".example.com"
				if (normalizedHost.endsWith(suffix)) {
					return true;
				}
			}
			else if (normalizedHost.equals(normalizedAllowed)) {
				return true;
			}
		}
		return false;
	}

	private static byte[] readWithSizeLimit(InputStream inputStream, int maxBytes) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int totalRead = 0;
		int bytesRead;
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			totalRead += bytesRead;
			if (totalRead > maxBytes) {
				throw new SecurityException(
						"Media URL response exceeds maximum allowed size of " + maxBytes + " bytes");
			}
			output.write(buffer, 0, bytesRead);
		}
		return output.toByteArray();
	}

	private static RestClient createSsrfSafeRestClient() {
		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
			.register("http", new SsrfBlockingPlainSocketFactory())
			.register("https", new SsrfBlockingSSLSocketFactory(SSLConnectionSocketFactory.getSocketFactory()))
			.build();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(
				socketFactoryRegistry, null, null, null, null, new SsrfSafeDnsResolver(), null);
		connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
			.setConnectTimeout(Timeout.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
			.setSocketTimeout(Timeout.ofSeconds(DEFAULT_SOCKET_TIMEOUT_SECONDS))
			.build());

		CloseableHttpClient httpClient = HttpClients.custom()
			.setConnectionManager(connectionManager)
			.disableRedirectHandling()
			.build();
		return RestClient.builder().requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient)).build();
	}

	/**
	 * Checks the resolved {@link InetAddress} in {@code remoteAddress} and throws
	 * {@link SecurityException} if it is a blocked internal address. Called by both
	 * socket factories at connect time — after DNS resolution — so it catches raw IP
	 * literals that bypass the {@link SsrfSafeDnsResolver}. Thrown as an unchecked
	 * {@link RuntimeException} so it propagates through Spring RestClient without being
	 * wrapped in {@link org.springframework.web.client.ResourceAccessException}.
	 */
	private static void assertNotBlockedAddress(InetSocketAddress remoteAddress, HttpHost host) {
		InetAddress address = remoteAddress.getAddress();
		if (address != null && URLValidator.isBlockedAddress(address)) {
			throw new SecurityException("Connection to blocked internal address " + address.getHostAddress()
					+ " rejected for host '" + host.getHostName() + "'");
		}
	}

	/**
	 * Plain-HTTP socket factory that blocks connections to internal addresses at connect
	 * time. Extends {@link PlainConnectionSocketFactory} and delegates to it after the
	 * address check, preserving all default socket behaviour.
	 */
	private static final class SsrfBlockingPlainSocketFactory extends PlainConnectionSocketFactory {

		@Override
		public Socket connectSocket(TimeValue connectTimeout, Socket socket, HttpHost host,
				InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context)
				throws IOException {
			assertNotBlockedAddress(remoteAddress, host);
			return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
		}

	}

	/**
	 * TLS socket factory that blocks connections to internal addresses at connect time.
	 * Wraps an {@link SSLConnectionSocketFactory} delegate and performs the address check
	 * before handing off to it, preserving all TLS configuration (cipher suites, hostname
	 * verification, etc.).
	 */
	private static final class SsrfBlockingSSLSocketFactory implements LayeredConnectionSocketFactory {

		private final SSLConnectionSocketFactory delegate;

		SsrfBlockingSSLSocketFactory(SSLConnectionSocketFactory delegate) {
			this.delegate = delegate;
		}

		@Override
		public Socket createSocket(HttpContext context) throws IOException {
			return this.delegate.createSocket(context);
		}

		@Override
		public Socket connectSocket(TimeValue connectTimeout, Socket socket, HttpHost host,
				InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context)
				throws IOException {
			assertNotBlockedAddress(remoteAddress, host);
			return this.delegate.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
		}

		@Override
		public Socket createLayeredSocket(Socket socket, String target, int port, HttpContext context)
				throws IOException {
			return this.delegate.createLayeredSocket(socket, target, port, context);
		}

	}

	/**
	 * DNS resolver that rejects hostnames resolving to internal addresses. Acts as an
	 * early-rejection layer for hostname-based requests, complementing the socket-level
	 * check in {@link SsrfBlockingPlainSocketFactory} and
	 * {@link SsrfBlockingSSLSocketFactory} which covers raw IP literals that skip DNS
	 * resolution entirely.
	 */
	private static final class SsrfSafeDnsResolver implements DnsResolver {

		@Override
		public InetAddress[] resolve(String host) throws UnknownHostException {
			InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
			for (InetAddress address : addresses) {
				if (URLValidator.isBlockedAddress(address)) {
					// Throw SecurityException (RuntimeException) rather than
					// UnknownHostException so it propagates through Spring RestClient
					// without being wrapped in ResourceAccessException.
					throw new SecurityException(
							"Host '" + host + "' resolves to a blocked internal address: " + address.getHostAddress());
				}
			}
			return addresses;
		}

		@Override
		public String resolveCanonicalHostname(String host) throws UnknownHostException {
			return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
		}

	}

}
