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

package org.springframework.ai.mcp.annotation;

/**
 * A sandbox permission requested by an MCP App UI resource per SEP-1865.
 *
 * <p>
 * Servers declare which browser capabilities their UI needs. Hosts MAY honor these by
 * setting appropriate iframe {@code allow} attributes. Apps SHOULD NOT assume permissions
 * are granted; use JS feature detection as fallback.
 *
 * <p>
 * On the wire, permissions are serialized into {@code _meta.ui.permissions} as a JSON
 * object keyed by the wire value, each mapped to an empty object. When no permission is
 * requested, {@code permissions} is omitted:
 *
 * <pre>
 * {
 *   "permissions": { "camera": {}, "clipboardWrite": {} }
 * }
 * </pre>
 *
 * @author Alexandros Pappas
 * @see McpResource#permissions()
 */
public enum McpPermission {

	/**
	 * Request camera access. Maps to Permission Policy {@code camera} feature.
	 */
	CAMERA("camera"),

	/**
	 * Request microphone access. Maps to Permission Policy {@code microphone} feature.
	 */
	MICROPHONE("microphone"),

	/**
	 * Request geolocation access. Maps to Permission Policy {@code geolocation} feature.
	 */
	GEOLOCATION("geolocation"),

	/**
	 * Request clipboard write access. Maps to Permission Policy {@code clipboard-write}
	 * feature.
	 */
	CLIPBOARD_WRITE("clipboardWrite");

	private final String wireValue;

	McpPermission(String wireValue) {
		this.wireValue = wireValue;
	}

	/**
	 * Returns the key used for this permission in {@code _meta.ui.permissions}.
	 * @return the wire value, e.g. "camera" or "clipboardWrite"
	 */
	public String getWireValue() {
		return this.wireValue;
	}

}
