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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link URLValidator#assertNoInternalAddress} — the pre-flight SSRF guard.
 * Numeric IPs are resolved by the JDK without a network round-trip, so these tests run
 * offline and are reliable in CI.
 */
class URLValidatorTest {

	// -------------------------------------------------------------------------
	// Loopback: localhost explicitly allowed by old regex
	// -------------------------------------------------------------------------

	@ParameterizedTest(name = "assertNoInternalAddress blocks loopback: {0}")
	@ValueSource(strings = { "127.0.0.1", "127.0.0.2", "::1" })
	void loopbackThrowsSecurityException(String host) {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress(host)).isInstanceOf(SecurityException.class)
			.hasMessageContaining(host);
	}

	@Test
	void localhostThrowsSecurityException() {
		// "localhost" resolves to 127.0.0.1 — the old regex explicitly allowed it
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("localhost"))
			.isInstanceOf(SecurityException.class);
	}

	// -------------------------------------------------------------------------
	// Link-local: AWS IMDS (169.254.169.254)
	// -------------------------------------------------------------------------

	@ParameterizedTest(name = "assertNoInternalAddress blocks link-local: {0}")
	@ValueSource(strings = { "169.254.169.254", "169.254.0.1" })
	void awsImdsThrowsSecurityException(String host) {
		// Primary scenario: AWS IMDS credential theft
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress(host)).isInstanceOf(SecurityException.class)
			.hasMessageContaining(host);
	}

	// -------------------------------------------------------------------------
	// Site-local — private network ranges
	// -------------------------------------------------------------------------

	@ParameterizedTest(name = "assertNoInternalAddress blocks site-local: {0}")
	@ValueSource(strings = { "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255", "192.168.0.1",
			"192.168.255.255" })
	void privateRangesThrowsSecurityException(String host) {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress(host)).isInstanceOf(SecurityException.class)
			.hasMessageContaining(host);
	}

	// -------------------------------------------------------------------------
	// Wildcard / any-local
	// -------------------------------------------------------------------------

	@Test
	void anyLocalThrowsSecurityException() {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("0.0.0.0")).isInstanceOf(SecurityException.class);
	}

	// -------------------------------------------------------------------------
	// Unresolvable host — fail-closed
	// -------------------------------------------------------------------------

	@Test
	void unknownHostThrowsSecurityException() {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("this-host-does-not-exist.invalid"))
			.isInstanceOf(SecurityException.class)
			.hasMessageContaining("Failed to resolve host");
	}

	// -------------------------------------------------------------------------
	// Internal domain names — metadata.google.internal
	// Not tested by DNS resolution because the domain is not guaranteed to resolve
	// in CI. The SsrfSafeDnsResolver in MediaFetcher provides the connect-time
	// defence for such domains (see MediaFetcherTest).
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// isBlockedAddress — unit-level coverage of each flag
	// -------------------------------------------------------------------------

	@Test
	void isBlockedAddressPublicIpv4ReturnsFalse() throws Exception {
		// 8.8.8.8 is a well-known public IP; numeric resolution needs no DNS lookup
		java.net.InetAddress google = java.net.InetAddress.getByName("8.8.8.8");
		assertThat(URLValidator.isBlockedAddress(google)).isFalse();
	}

	@Test
	void doesNotThrowForPublicNumericIp() {
		// 8.8.8.8 parsed without DNS; must not be blocked
		assertThatCode(() -> URLValidator.assertNoInternalAddress("8.8.8.8")).doesNotThrowAnyException();
	}

}
