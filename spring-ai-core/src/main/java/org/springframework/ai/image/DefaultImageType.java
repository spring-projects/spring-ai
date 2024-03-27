/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.image;

import java.util.Arrays;

/**
 * Default Image Type
 *
 * @author youngmon
 * @version 0.8.1
 */
public enum DefaultImageType implements ImageType<DefaultImageType, String> {

	URL("url"), B64("b64");

	private final String value;

	DefaultImageType(final String value) {
		this.value = value;
	}

	@Override
	public String getValue() {
		return this.value;
	}

	public static DefaultImageType fromValue(final String value) {
		return Arrays.stream(values())
			.filter(v -> v.value.equals(value))
			.findAny()
			.orElseThrow(() -> new IllegalArgumentException("Invalid Value"));
	}

}
