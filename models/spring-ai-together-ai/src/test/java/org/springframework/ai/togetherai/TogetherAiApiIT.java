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

package org.springframework.ai.togetherai;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.togetherai.api.TogetherAiApi;
import org.springframework.ai.togetherai.api.TogetherAiImageOptions;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TOGETHERAI_API_KEY", matches = ".+")
class TogetherAiApiIT {

	private TogetherAiApi togetherAiApi;

	@BeforeEach
	void setUp() {
		this.togetherAiApi = new TogetherAiApi(System.getenv("TOGETHERAI_API_KEY"));
	}

	@Test
	void generateImage_shouldReturnBase64Png_whenResponseFormatIsBase64AndOutputFormatIsPng() {
		TogetherAiApi.GenerateImageRequest request = TogetherAiApi.GenerateImageRequest.builder()
			.prompt("A green apple on a plate with leaf")
			.model("black-forest-labs/FLUX.1-schnell")
			.height(1024)
			.width(1024)
			.responseFormat(TogetherAiImageOptions.ResponseFormat.BASE64.getValue())
			.outputFormat(TogetherAiImageOptions.OutputFormat.PNG.getValue())
			.build();
		TogetherAiApi.GenerateImageResponse response = this.togetherAiApi.generateImage(request);

		assertThat(response).isNotNull();
		List<TogetherAiApi.GenerateImageResponse.Image> data = response.data();
		assertThat(data).hasSize(1);
		var firstImage = data.get(0);
		assertThat(firstImage.b64Json()).isNotEmpty();
		assertThat(firstImage.url()).isNull();
	}

	@Test
	void generateImage_shouldReturnBase64Jpeg_whenResponseFormatIsBase64AndOutputFormatIsJpeg() {
		TogetherAiApi.GenerateImageRequest request = TogetherAiApi.GenerateImageRequest.builder()
			.prompt("A green apple on a plate with leaf")
			.model("black-forest-labs/FLUX.1-schnell")
			.height(1024)
			.width(1024)
			.responseFormat(TogetherAiImageOptions.ResponseFormat.BASE64.getValue())
			.outputFormat(TogetherAiImageOptions.OutputFormat.JPEG.getValue())
			.build();
		TogetherAiApi.GenerateImageResponse response = this.togetherAiApi.generateImage(request);

		assertThat(response).isNotNull();
		List<TogetherAiApi.GenerateImageResponse.Image> data = response.data();
		assertThat(data).hasSize(1);
		var firstImage = data.get(0);
		assertThat(firstImage.b64Json()).isNotEmpty();
		assertThat(firstImage.url()).isNull();
	}

	@Test
	void generateImage_shouldReturnUrlPng_whenResponseFormatIsUrlAndOutputFormatIsPng() {
		TogetherAiApi.GenerateImageRequest request = TogetherAiApi.GenerateImageRequest.builder()
			.prompt("A green apple on a plate with leaf")
			.model("black-forest-labs/FLUX.1-schnell")
			.height(1024)
			.width(1024)
			.responseFormat(TogetherAiImageOptions.ResponseFormat.URL.getValue())
			.outputFormat(TogetherAiImageOptions.OutputFormat.PNG.getValue())
			.build();
		TogetherAiApi.GenerateImageResponse response = this.togetherAiApi.generateImage(request);

		assertThat(response).isNotNull();
		List<TogetherAiApi.GenerateImageResponse.Image> data = response.data();
		assertThat(data).hasSize(1);
		var firstImage = data.get(0);
		assertThat(firstImage.b64Json()).isNull();
		assertThat(firstImage.url()).isNotEmpty();
	}

	@Test
	void generateImage_shouldReturnUrlJpeg_whenResponseFormatIsUrlAndOutputFormatIsJpeg() {
		TogetherAiApi.GenerateImageRequest request = TogetherAiApi.GenerateImageRequest.builder()
			.prompt("A green apple on a plate with leaf")
			.model("black-forest-labs/FLUX.1-schnell")
			.height(1024)
			.width(1024)
			.responseFormat(TogetherAiImageOptions.ResponseFormat.URL.getValue())
			.outputFormat(TogetherAiImageOptions.OutputFormat.JPEG.getValue())
			.build();
		TogetherAiApi.GenerateImageResponse response = this.togetherAiApi.generateImage(request);

		assertThat(response).isNotNull();
		List<TogetherAiApi.GenerateImageResponse.Image> data = response.data();
		assertThat(data).hasSize(1);
		var firstImage = data.get(0);
		assertThat(firstImage.b64Json()).isNull();
		assertThat(firstImage.url()).isNotEmpty();
	}

}
