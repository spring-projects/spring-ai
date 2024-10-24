/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.bedrock.converse.api;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Utility class for detecting and normalizing URLs. Intended for use with multimodal user
 * inputs.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class URLValidator {

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

	/**
	 * Quick validation using regex pattern Good for basic checks but may not catch all
	 * edge cases
	 */
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

			// Validate host (not empty and contains at least one dot, unless it's
			// localhost)
			String host = url.getHost();
			if (host == null || host.isEmpty()) {
				return false;
			}
			if (!host.equals("localhost") && !host.contains(".")) {
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
	 * Attempts to fix common URL issues Adds protocol if missing, removes extra spaces
	 */
	public static String normalizeURL(String urlString) {
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