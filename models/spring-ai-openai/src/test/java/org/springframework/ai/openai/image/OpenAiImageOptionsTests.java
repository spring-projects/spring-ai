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

package org.springframework.ai.openai.image;

import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.openai.OpenAiImageOptions;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiImageOptionsTests {

	@Test
	void genericImageOptionsAreMerged() {
		ImageOptions source = ImageOptionsBuilder.builder()
			.model("generic-model")
			.N(2)
			.width(1024)
			.height(1024)
			.responseFormat("b64_json")
			.style("vivid")
			.build();

		OpenAiImageOptions merged = OpenAiImageOptions.builder().merge(source).build();

		assertThat(merged.getModel()).isEqualTo("generic-model");
		assertThat(merged.getN()).isEqualTo(2);
		assertThat(merged.getWidth()).isEqualTo(1024);
		assertThat(merged.getHeight()).isEqualTo(1024);
		assertThat(merged.getResponseFormat()).isEqualTo("b64_json");
		assertThat(merged.getStyle()).isEqualTo("vivid");
	}

}
