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
	// IPv4 shared address space / carrier-grade NAT (RFC 6598)
	// -------------------------------------------------------------------------

	@ParameterizedTest(name = "assertNoInternalAddress blocks CGNAT: {0}")
	@ValueSource(strings = { "100.64.0.1", "100.100.100.200", "100.127.255.255" })
	void cgnatThrowsSecurityException(String host) {
		// 100.100.100.200 is Alibaba Cloud's IPv6/CGNAT-addressed metadata service
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress(host)).isInstanceOf(SecurityException.class)
			.hasMessageContaining(host);
	}

	@ParameterizedTest(name = "assertNoInternalAddress does not block addresses outside CGNAT: {0}")
	@ValueSource(strings = { "100.63.255.255", "100.128.0.0" })
	void justOutsideCgnatDoesNotThrow(String host) {
		assertThatCode(() -> URLValidator.assertNoInternalAddress(host)).doesNotThrowAnyException();
	}

	// -------------------------------------------------------------------------
	// IPv4 limited broadcast, multicast, and benchmarking ranges
	// -------------------------------------------------------------------------

	@Test
	void limitedBroadcastThrowsSecurityException() {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("255.255.255.255"))
			.isInstanceOf(SecurityException.class);
	}

	@Test
	void multicastThrowsSecurityException() {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("224.0.0.1"))
			.isInstanceOf(SecurityException.class);
	}

	@ParameterizedTest(name = "assertNoInternalAddress blocks IPv4 benchmarking: {0}")
	@ValueSource(strings = { "198.18.0.1", "198.19.255.255" })
	void benchmarkingIpv4ThrowsSecurityException(String host) {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress(host)).isInstanceOf(SecurityException.class);
	}

	// -------------------------------------------------------------------------
	// IPv4 reserved range 240.0.0.0/4 (former class E) — the limited broadcast
	// address above is the top of this range, not an isolated /32
	// -------------------------------------------------------------------------

	@ParameterizedTest(name = "assertNoInternalAddress blocks IPv4 reserved: {0}")
	@ValueSource(strings = { "240.0.0.1", "250.1.2.3", "255.0.0.1", "255.255.255.254" })
	void reservedIpv4ThrowsSecurityException(String host) {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress(host)).isInstanceOf(SecurityException.class);
	}

	@Test
	void justOutsideReservedIpv4DoesNotThrow() {
		// 223.255.255.255 is the last public address below the multicast range that
		// precedes 240.0.0.0/4; it must remain allowed
		assertThatCode(() -> URLValidator.assertNoInternalAddress("223.255.255.255")).doesNotThrowAnyException();
	}

	// -------------------------------------------------------------------------
	// IPv6 Unique Local Addresses (RFC 4193) — the modern successor to the
	// deprecated site-local range that isSiteLocalAddress() already covers
	// -------------------------------------------------------------------------

	@ParameterizedTest(name = "assertNoInternalAddress blocks IPv6 ULA: {0}")
	@ValueSource(strings = { "fc00::1", "fd00::1", "fd00:ec2::254", "fd12:3456::a" })
	void uniqueLocalIpv6ThrowsSecurityException(String host) {
		// fd00:ec2::254 is AWS's documented IPv6 IMDS endpoint for Nitro instances
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress(host)).isInstanceOf(SecurityException.class);
	}

	// -------------------------------------------------------------------------
	// IPv6 discard-only and benchmarking ranges
	// -------------------------------------------------------------------------

	@Test
	void discardOnlyIpv6ThrowsSecurityException() {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("100::1")).isInstanceOf(SecurityException.class);
	}

	@Test
	void benchmarkingIpv6ThrowsSecurityException() {
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("2001:2::1"))
			.isInstanceOf(SecurityException.class);
	}

	// -------------------------------------------------------------------------
	// Tunneling / embedding schemes — the embedded IPv4 address must be
	// recovered and checked, not just the outer IPv6 literal
	// -------------------------------------------------------------------------

	@Test
	void ipv4MappedEmbeddingLoopbackThrowsSecurityException() {
		// ::ffff:127.0.0.1 — IPv4-mapped address embedding the loopback address
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("::ffff:127.0.0.1"))
			.isInstanceOf(SecurityException.class);
	}

	@Test
	void nat64EmbeddingLoopbackThrowsSecurityException() {
		// 64:ff9b::7f00:1 — NAT64 embedding 127.0.0.1
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("64:ff9b::7f00:1"))
			.isInstanceOf(SecurityException.class);
	}

	@Test
	void sixToFourEmbeddingLoopbackThrowsSecurityException() {
		// 2002:7f00:1:: — 6to4 embedding 127.0.0.1 in bits 16-47
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("2002:7f00:1::"))
			.isInstanceOf(SecurityException.class);
	}

	@Test
	void teredoEmbeddingLoopbackThrowsSecurityException() {
		// 2001::80ff:fffe — Teredo embedding 127.0.0.1, bitwise negated per RFC 4380
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress("2001::80ff:fffe"))
			.isInstanceOf(SecurityException.class);
	}

	@ParameterizedTest(name = "assertNoInternalAddress blocks local-use NAT64: {0}")
	@ValueSource(strings = { "64:ff9b:1::", "64:ff9b:1::7f00:1", "64:ff9b:1:ffff::1", "64:ff9b:1:0:a00:1::" })
	void localUseNat64ThrowsSecurityException(String host) {
		// 64:ff9b:1::/48 is the RFC 8215 local-use translation prefix. It is blocked
		// on the prefix alone, so it is rejected regardless of which offset the
		// embedded IPv4 address sits at — including 64:ff9b:1:: with none at all.
		assertThatThrownBy(() -> URLValidator.assertNoInternalAddress(host)).isInstanceOf(SecurityException.class);
	}

	@Test
	void nat64OutsideLocalUsePrefixDoesNotThrow() {
		// 64:ff9b:2::/48 is not a special-purpose prefix — neither the well-known
		// 64:ff9b::/96 NAT64 prefix nor the local-use 64:ff9b:1::/48 one — so
		// blocking it would mean the local-use check had over-matched
		assertThatCode(() -> URLValidator.assertNoInternalAddress("64:ff9b:2::808:808")).doesNotThrowAnyException();
	}

	@Test
	void sixToFourEmbeddingPublicAddressDoesNotThrow() {
		// 2002:808:808:: — 6to4 embedding the public address 8.8.8.8; must remain
		// allowed, otherwise the tunnel-unwrapping logic would over-block
		assertThatCode(() -> URLValidator.assertNoInternalAddress("2002:808:808::")).doesNotThrowAnyException();
	}

	@Test
	void publicIpv6DoesNotThrow() {
		// A real public IPv6 address (Google Public DNS) must remain allowed
		assertThatCode(() -> URLValidator.assertNoInternalAddress("2001:4860:4860::8888")).doesNotThrowAnyException();
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
