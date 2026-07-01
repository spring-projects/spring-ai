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

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Utility class for detecting and normalizing URLs. Intended for use with multimodal user
 * inputs.
 *
 * @author Christian Tzolov
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
			String protocol = url.getProtocol().toLowerCase();
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
	 * Returns {@code true} if the given address is a loopback, link-local, site-local, or
	 * wildcard address. Covers both IPv4 and IPv6 private/internal ranges.
	 * @param address the address to test
	 * @return {@code true} if the address should be blocked
	 */
	public static boolean isBlockedAddress(InetAddress address) {
		return address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
				|| address.isAnyLocalAddress();
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
		if (!normalized.toLowerCase().startsWith("http://") && !normalized.toLowerCase().startsWith("https://")) {
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
