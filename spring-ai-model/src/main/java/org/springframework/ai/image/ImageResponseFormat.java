/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.image;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import org.springframework.util.StringUtils;

/**
 * Common response formats supported by image generation providers.
 *
 * @author Kuntal Maity
 */
public enum ImageResponseFormat {

	URL("url"),

	B64_JSON("b64_json"),

	/**
	 * PNG responses typically returned by providers when requesting raw image bytes.
	 */
	IMAGE_PNG("image/png"),

	/**
	 * JSON responses containing additional metadata or base64 encoded payloads.
	 */
	APPLICATION_JSON("application/json");

	private final String value;

	ImageResponseFormat(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return this.value;
	}

	@JsonCreator
	public static ImageResponseFormat fromValue(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return Arrays.stream(values())
			.filter(format -> format.value.equalsIgnoreCase(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unsupported image response format: " + value));
	}

}
