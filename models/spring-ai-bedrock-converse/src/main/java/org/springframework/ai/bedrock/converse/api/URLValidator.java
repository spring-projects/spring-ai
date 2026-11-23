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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Utility class for detecting and normalizing URLs. Intended for use with multimodal user
 * inputs.
 *
 * @author Christian Tzolov
 * @author Dimitar Proynov
 * @since 1.0.0
 */
public final class URLValidator {

	// Basic URL regex pattern
	// Protocol (http:// or https://)
	private static final Pattern URL_PATTERN = Pattern.compile("^(https?://)" +

			"((([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6})|" + // Domain name
			"(localhost))" + // OR localhost
			"(:[0-9]{1,5})?" + // Optional port
			"(/[\\w\\-./]*)*" + // Optional path
			"(\\?[\\w=&\\-.]*)?" + // Optional query parameters
			"(#[\\w-]*)?" + // Optional fragment
			"$");

	private URLValidator() {

	}

	/**
	 * Check if the string looks like a URL using a simple regex pattern to disstinct it
	 * from base64 or other text. This is a quick check to avoid unnecessary URL parsing
	 * for clearly non-URL strings.
	 * @deprecated This method is not sufficient for security-sensitive URL validation and
	 * should not be relied upon for security-critical checks. Use
	 * {@link #isValidURLStrict(String)} instead for robust validation.
	 */
	@Deprecated
	public static boolean isValidURLBasic(String urlString) {
		if (urlString == null || urlString.trim().isEmpty()) {
			return false;
		}
		return URL_PATTERN.matcher(urlString).matches();
	}

	/**
	 * Thorough validation using URL class More comprehensive but might be slower
	 * Validates protocol, host, port, and basic structure
	 */
	public static boolean isValidURLStrict(String urlString) {
		if (urlString == null || urlString.trim().isEmpty()) {
			return false;
		}

		try {
			URL url = new URL(urlString);
			// Additional validation by attempting to convert to URI
			url.toURI();

			// Ensure protocol is http or https
			String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
			if (!protocol.equals("http") && !protocol.equals("https")) {
				return false;
			}

			// Validate host (not empty)
			// IPv6 hosts contain ':' instead of '.', so skip the dot check for them
			String host = url.getHost();
			if (host == null || host.isEmpty()) {
				return false;
			}
			boolean isIPv6 = host.contains(":");
			if (!isIPv6 && !host.contains(".")) {
				return false;
			}

			// Block internal/private addresses (loopback, link-local, site-local)
			// including raw IP literals that bypass the dot-based localhost check
			try {
				assertNoInternalAddress(host);
			}
			catch (SecurityException e) {
				return false;
			}

			// Validate port (if specified)
			int port = url.getPort();
			if (port != -1 && (port < 1 || port > 65535)) {
				return false;
			}

			return true;
		}
		catch (MalformedURLException | URISyntaxException e) {
			return false;
		}
	}

	/**
	 * Resolves all IP addresses for the given hostname and throws
	 * {@link SecurityException} if any resolve to a loopback, link-local, site-local, or
	 * wildcard address. Protects against SSRF via internal network access (including IPv6
	 * equivalents) and limits exposure from DNS rebinding by checking all returned
	 * addresses.
	 * @param host the hostname to check
	 * @throws SecurityException if the host resolves to a blocked internal address or
	 * cannot be resolved
	 */
	public static void assertNoInternalAddress(String host) {
		try {
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (isBlockedAddress(address)) {
					throw new SecurityException("URL host '" + host + "' resolves to a blocked internal address: "
							+ address.getHostAddress());
				}
			}
		}
		catch (UnknownHostException e) {
			throw new SecurityException("Failed to resolve host: " + host, e);
		}
	}

	/**
	 * Returns {@code true} if the given address is not globally reachable and should
	 * therefore be treated as an internal SSRF target.
	 * <p>
	 * Beyond the loopback, link-local, private-use, wildcard, and multicast ranges the
	 * JDK helpers already classify as internal, this also covers ranges the JDK does not
	 * recognize: IPv6 Unique Local Addresses ({@code fc00::/7}, the modern successor to
	 * the deprecated site-local range that {@link Inet6Address#isSiteLocalAddress()}
	 * already covers), IPv4 shared address space / carrier-grade NAT
	 * ({@code 100.64.0.0/10}), the IPv4 reserved range ({@code 240.0.0.0/4}, whose top
	 * address is the limited broadcast address), the local-use NAT64 translation prefix
	 * ({@code 64:ff9b:1::/48}), and the IPv4/IPv6 benchmarking and discard-only ranges.
	 * Addresses that tunnel or embed an IPv4 address (6to4, Teredo, NAT64, IPv4-mapped
	 * IPv6) are unwrapped and the embedded address is checked in its own right, since
	 * classifying the outer address alone would miss an internal target hidden inside it.
	 * <p>
	 * Based on the IANA special-purpose address registries established by RFC 6890. Those
	 * registries are updated by IANA over time, independently of this RFC, so re-check
	 * them (not just this method) if a future report finds another gap:
	 * <a href="https://www.iana.org/assignments/iana-ipv4-special-registry/"> IPv4
	 * Special Registry</a> and
	 * <a href="https://www.iana.org/assignments/iana-ipv6-special-registry/"> IPv6
	 * Special Registry</a>.
	 * <p>
	 * </p>
	 * <b>NOTE:</b> There will never be a comprehensive list of blocked addresses. For
	 * fine-grained access control use a MediaFetcher with an allowlist.
	 * @param address the address to test
	 * @return {@code true} if the address should be blocked
	 */
	public static boolean isBlockedAddress(InetAddress address) {
		if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
				|| address.isAnyLocalAddress() || address.isMulticastAddress()) {
			return true;
		}
		if (address instanceof Inet4Address ipv4) {
			return isBlockedIpv4Range(ipv4.getAddress());
		}
		if (address instanceof Inet6Address ipv6) {
			return isBlockedIpv6Range(ipv6.getAddress());
		}
		return false;
	}

	private static boolean isBlockedIpv4Range(byte[] address) {
		int[] octet = unsignedBytesOf(address);
		// 0.0.0.0/8 - "This host on this network"
		if (octet[0] == 0) {
			return true;
		}
		// 100.64.0.0/10 - Shared Address Space (RFC 6598, carrier-grade NAT): the
		// second octet ranges over 64-127, i.e. the two /16 blocks 100.64.0.0/16
		// through 100.127.0.0/16
		if (octet[0] == 100 && octet[1] >= 64 && octet[1] <= 127) {
			return true;
		}
		// 198.18.0.0/15 - Network Interconnect Device Benchmark Testing: the two
		// /16 blocks 198.18.0.0/16 and 198.19.0.0/16
		if (octet[0] == 198 && (octet[1] == 18 || octet[1] == 19)) {
			return true;
		}
		// 240.0.0.0/4 - Reserved (the former class E space): the first octet ranges
		// over 240-255, which also covers the 255.255.255.255/32 limited broadcast
		// address sitting at the very top of that range
		return octet[0] >= 240;
	}

	private static boolean isBlockedIpv6Range(byte[] address) {
		int[] group = unsignedBytesOf(address);
		// fc00::/7 - Unique Local Address (RFC 4193): the first byte is either
		// 0xfc or 0xfd, i.e. the two /8 blocks fc00::/8 and fd00::/8
		if (group[0] == 0xfc || group[0] == 0xfd) {
			return true;
		}
		// 100::/64 - Discard-Only Address Block
		if (group[0] == 0x01 && group[1] == 0x00 && allZero(group, 2, 8)) {
			return true;
		}
		// 2001:2::/48 - Benchmarking
		if (group[0] == 0x20 && group[1] == 0x01 && group[2] == 0x00 && group[3] == 0x02 && allZero(group, 4, 6)) {
			return true;
		}
		// 64:ff9b:1::/48 - Local-Use IPv4/IPv6 Translation (RFC 8215). Blocked by
		// prefix rather than unwrapped like the well-known 64:ff9b::/96 prefix is:
		// RFC 8215 allows this prefix to be sub-delegated at any of the RFC 6052
		// lengths, so the embedded IPv4 address has no single fixed offset here. The
		// range is local-use in its entirety, so the outer prefix alone is decisive.
		if (group[0] == 0x00 && group[1] == 0x64 && group[2] == 0xff && group[3] == 0x9b && group[4] == 0x00
				&& group[5] == 0x01) {
			return true;
		}
		InetAddress embedded = extractEmbeddedIpv4(group);
		return embedded != null && isBlockedAddress(embedded);
	}

	/**
	 * Extracts the IPv4 address embedded in an IPv6 tunneling/mapping scheme, or
	 * {@code null} if {@code group} does not use one of them. Each check below compares
	 * against the address's unsigned byte values in the same base (hex) used to describe
	 * the scheme's address layout in its RFC, so it can be read side-by-side with the RFC
	 * without converting anything.
	 * @param group the 16 unsigned byte values of the IPv6 address
	 * @return the embedded IPv4 address, or {@code null} if none of the known schemes
	 * match
	 */
	private static @Nullable InetAddress extractEmbeddedIpv4(int[] group) {
		try {
			// ::ffff:0:0/96 - IPv4-mapped address: bytes 0-9 are zero, bytes
			// 10-11 are 0xff, and the embedded IPv4 address occupies bytes 12-15
			if (allZero(group, 0, 10) && group[10] == 0xff && group[11] == 0xff) {
				return toInet4(group, 12);
			}
			// 64:ff9b::/96 - NAT64: bytes 0-3 spell out "0064ff9b", bytes 4-11
			// are zero, and the embedded IPv4 address occupies bytes 12-15
			if (group[0] == 0x00 && group[1] == 0x64 && group[2] == 0xff && group[3] == 0x9b && allZero(group, 4, 12)) {
				return toInet4(group, 12);
			}
			// 2002::/16 - 6to4 (RFC 3056): the embedded IPv4 address occupies
			// bytes 2-5, right after the "2002" prefix
			if (group[0] == 0x20 && group[1] == 0x02) {
				return toInet4(group, 2);
			}
			// 2001::/32 - Teredo (RFC 4380): the embedded IPv4 address occupies
			// bytes 12-15, but each byte is stored as its one's complement (255
			// minus the real value) so that NAT devices rewriting the outer
			// address don't also rewrite what looks like a real IPv4 address
			if (group[0] == 0x20 && group[1] == 0x01 && group[2] == 0x00 && group[3] == 0x00) {
				return InetAddress
					.getByAddress(toByteArray(255 - group[12], 255 - group[13], 255 - group[14], 255 - group[15]));
			}
		}
		catch (UnknownHostException ex) {
			// unreachable: InetAddress.getByAddress with a 4-byte array never throws
		}
		return null;
	}

	private static InetAddress toInet4(int[] group, int offset) throws UnknownHostException {
		return InetAddress
			.getByAddress(toByteArray(group[offset], group[offset + 1], group[offset + 2], group[offset + 3]));
	}

	private static byte[] toByteArray(int... unsignedBytes) {
		byte[] bytes = new byte[unsignedBytes.length];
		for (int i = 0; i < unsignedBytes.length; i++) {
			bytes[i] = (byte) unsignedBytes[i];
		}
		return bytes;
	}

	private static int[] unsignedBytesOf(byte[] address) {
		int[] unsignedBytes = new int[address.length];
		for (int i = 0; i < address.length; i++) {
			unsignedBytes[i] = Byte.toUnsignedInt(address[i]);
		}
		return unsignedBytes;
	}

	private static boolean allZero(int[] unsignedBytes, int from, int to) {
		for (int i = from; i < to; i++) {
			if (unsignedBytes[i] != 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Attempts to fix common URL issues Adds protocol if missing, removes extra spaces
	 */
	public static @Nullable String normalizeURL(@Nullable String urlString) {
		if (urlString == null || urlString.trim().isEmpty()) {
			return null;
		}

		String normalized = urlString.trim();

		// Add protocol if missing
		if (!normalized.toLowerCase(Locale.ROOT).startsWith("http://")
				&& !normalized.toLowerCase(Locale.ROOT).startsWith("https://")) {
			normalized = "https://" + normalized;
		}

		// Remove multiple forward slashes in path (except after protocol)
		normalized = normalized.replaceAll("(?<!:)/{2,}", "/");

		// Remove trailing slash (unless it's just protocol://host)
		if (normalized.matches("https?://[^/]+/+$")) {
			normalized = normalized.replaceAll("/+$", "");
		}

		return normalized;
	}

}
