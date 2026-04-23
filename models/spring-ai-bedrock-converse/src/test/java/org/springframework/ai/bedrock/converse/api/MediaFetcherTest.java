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

import java.net.URI;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link MediaFetcher} covering the allowlist, SSRF blocklist, and size-limit
 * protections.
 */
class MediaFetcherTest {

	private RestClient.Builder restClientBuilder;

	private MockRestServiceServer mockServer;

	@BeforeEach
	void setUp() {
		this.restClientBuilder = RestClient.builder();
		this.mockServer = MockRestServiceServer.bindTo(this.restClientBuilder).build();
	}

	// -------------------------------------------------------------------------
	// Allowlist rejection — no network call needed
	// -------------------------------------------------------------------------

	@Test
	void fetchHostNotInAllowlistThrowsSecurityException() {
		MediaFetcher fetcher = new MediaFetcher(Set.of("trusted.com"));
		assertThatThrownBy(() -> fetcher.fetch(URI.create("http://evil.com/image.png")))
			.isInstanceOf(SecurityException.class)
			.hasMessageContaining("evil.com");
	}

	@Test
	void fetchWildcardDoesNotMatchApexDomainThrowsSecurityException() {
		// *.example.com must NOT match example.com itself
		MediaFetcher fetcher = new MediaFetcher(Set.of("*.example.com"));
		assertThatThrownBy(() -> fetcher.fetch(URI.create("http://example.com/image.png")))
			.isInstanceOf(SecurityException.class);
	}

	@Test
	void fetchWildcardDoesNotMatchUnrelatedDomainThrowsSecurityException() {
		MediaFetcher fetcher = new MediaFetcher(Set.of("*.example.com"));
		assertThatThrownBy(() -> fetcher.fetch(URI.create("http://evil.notexample.com/image.png")))
			.isInstanceOf(SecurityException.class);
	}

	// -------------------------------------------------------------------------
	// Allowlist pass-through — via MockRestServiceServer
	// -------------------------------------------------------------------------

	@Test
	void fetchExactHostInAllowlistFetchSucceeds() {
		MediaFetcher fetcher = new MediaFetcher(Set.of("example.com"), this.restClientBuilder.build());
		this.mockServer.expect(requestTo("http://example.com/image.png"))
			.andRespond(withSuccess("imagedata", MediaType.IMAGE_PNG));

		byte[] result = fetcher.fetch(URI.create("http://example.com/image.png"));

		assertThat(result).isEqualTo("imagedata".getBytes());
		this.mockServer.verify();
	}

	@Test
	void fetchExactHostCaseInsensitiveFetchSucceeds() {
		// Allowlist entry is uppercase; URI host is lowercase
		MediaFetcher fetcher = new MediaFetcher(Set.of("EXAMPLE.COM"), this.restClientBuilder.build());
		this.mockServer.expect(requestTo("http://example.com/image.png"))
			.andRespond(withSuccess("imagedata", MediaType.IMAGE_PNG));

		byte[] result = fetcher.fetch(URI.create("http://example.com/image.png"));

		assertThat(result).isNotEmpty();
		this.mockServer.verify();
	}

	@Test
	void fetchWildcardMatchesSubdomainFetchSucceeds() {
		MediaFetcher fetcher = new MediaFetcher(Set.of("*.example.com"), this.restClientBuilder.build());
		this.mockServer.expect(requestTo("http://cdn.example.com/image.png"))
			.andRespond(withSuccess("imagedata", MediaType.IMAGE_PNG));

		byte[] result = fetcher.fetch(URI.create("http://cdn.example.com/image.png"));

		assertThat(result).isNotEmpty();
		this.mockServer.verify();
	}

	@Test
	void fetchEmptyAllowlistNoAllowlistEnforced() {
		// Empty allowlist → no allowlist check; only the SSRF blocklist applies
		MediaFetcher fetcher = new MediaFetcher(Set.of(), this.restClientBuilder.build());
		this.mockServer.expect(requestTo("http://any-host.com/image.png"))
			.andRespond(withSuccess("imagedata", MediaType.IMAGE_PNG));

		byte[] result = fetcher.fetch(URI.create("http://any-host.com/image.png"));

		assertThat(result).isNotEmpty();
		this.mockServer.verify();
	}

	// -------------------------------------------------------------------------
	// SSRF blocking — connect-time defence (real MediaFetcher, no mock)
	//
	// Numeric IPs are resolved by the JDK without a real DNS round-trip, so
	// these tests run offline. Both SsrfSafeDnsResolver and the socket-level
	// factories throw SecurityException (RuntimeException), which propagates
	// through Spring RestClient without being wrapped in RestClientException.
	// -------------------------------------------------------------------------

	@Test
	void fetchLoopbackAddressBlockedAtConnectTime() {
		MediaFetcher fetcher = new MediaFetcher();
		assertThatThrownBy(() -> fetcher.fetch(URI.create("http://127.0.0.1/image.png")))
			.isInstanceOf(SecurityException.class);
	}

	@Test
	void fetchAwsImdsAddressBlockedAtConnectTime() {
		// 169.254.169.254 must never be reached
		MediaFetcher fetcher = new MediaFetcher();
		assertThatThrownBy(() -> fetcher.fetch(URI.create("http://169.254.169.254/latest/meta-data/iam/")))
			.isInstanceOf(SecurityException.class);
	}

	@Test
	void fetchSiteLocalAddressBlockedAtConnectTime() {
		MediaFetcher fetcher = new MediaFetcher();
		assertThatThrownBy(() -> fetcher.fetch(URI.create("http://10.0.0.1/image.png")))
			.isInstanceOf(SecurityException.class);
	}

	// -------------------------------------------------------------------------
	// Size-limit protection
	// -------------------------------------------------------------------------

	@Test
	void fetchContentLengthExceedsLimitThrowsSecurityException() {
		MediaFetcher fetcher = new MediaFetcher(Set.of(), this.restClientBuilder.build());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentLength((long) MediaFetcher.DEFAULT_MAX_FETCH_SIZE_BYTES + 1);
		this.mockServer.expect(requestTo("http://cdn.example.com/big.png"))
			.andRespond(withStatus(HttpStatus.OK).contentType(MediaType.IMAGE_PNG).headers(headers));

		assertThatThrownBy(() -> fetcher.fetch(URI.create("http://cdn.example.com/big.png")))
			.isInstanceOf(SecurityException.class)
			.hasMessageContaining("exceeds maximum allowed size");
	}

}
