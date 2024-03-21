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
package org.springframework.ai.stabilityai.image;

import java.util.Arrays;
import org.springframework.ai.image.ImageType;

/**
 * Represents the types of images supported by the StabilityAi image generation API. This
 * enum provides a type-safe way to specify and retrieve the image types used in
 * StabilityAi image generation requests and responses.
 * <p>
 * It includes standard image types such as Base64.
 * </p>
 *
 * @implNote This enum implements the {@link ImageType} interface, enabling it to be used
 * in a generic manner across the image processing framework.
 * @author youngmon
 * @version 0.8.1
 */
public enum StabilityAiImageType implements ImageType<StabilityAiImageType, String> {

	BASE64("base64");

	private final String value;

	StabilityAiImageType(final String value) {
		this.value = value;
	}

	@Override
	public String getValue() {
		return this.value;
	}

	@Override
	public StabilityAiImageType fromValue(String value) {
		return Arrays.stream(values())
			.filter(v -> v.value.equals(value))
			.findAny()
			.orElseThrow(() -> new IllegalArgumentException("Invalid Value"));
	}

}
