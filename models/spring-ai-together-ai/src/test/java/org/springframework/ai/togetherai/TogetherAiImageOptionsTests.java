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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.togetherai.api.TogetherAiApi;
import org.springframework.ai.togetherai.api.TogetherAiImageOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TogetherAiImageOptionsTests {

	@Mock
	private TogetherAiApi togetherAiApi;

	@BeforeEach
	void setUp() {
		TogetherAiApi.GenerateImageResponse generateImageResponse = new TogetherAiApi.GenerateImageResponse("id",
				"model", "image", List.of());
		when(this.togetherAiApi.generateImage(ArgumentMatchers.any())).thenReturn(generateImageResponse);
	}

	@Test
	void shouldPreferRuntimeOptionsOverDefaultOptions() {
		TogetherAiImageOptions defaultOptions = TogetherAiImageOptions.builder()
			.model("default-model")
			.steps(20)
			.imageUrl("https://example.com/default.png")
			.seed(1234L)
			.n(1)
			.height(512)
			.width(512)
			.negativePrompt("default negative prompt")
			.responseFormat(TogetherAiImageOptions.ResponseFormat.BASE64)
			.guidanceScale(7.0f)
			.outputFormat(TogetherAiImageOptions.OutputFormat.PNG)
			.imageLoras(List.of(new TogetherAiImageOptions.ImageLora("default-lora", 0.5f)))
			.referenceImages(List.of("https://example.com/default-reference.png"))
			.disableSafetyChecker(false)
			.build();

		TogetherAiImageOptions runtimeOptions = TogetherAiImageOptions.builder()
			.model("runtime-model")
			.steps(50)
			.imageUrl("https://example.com/runtime.png")
			.seed(5678L)
			.n(2)
			.height(768)
			.width(1024)
			.negativePrompt("runtime negative prompt")
			.responseFormat(TogetherAiImageOptions.ResponseFormat.URL)
			.guidanceScale(14.0f)
			.outputFormat(TogetherAiImageOptions.OutputFormat.JPEG)
			.imageLoras(List.of(new TogetherAiImageOptions.ImageLora("runtime-lora", 1.0f)))
			.referenceImages(List.of("https://example.com/runtime-reference.png"))
			.disableSafetyChecker(true)
			.build();

		TogetherAiImageModel imageModel = new TogetherAiImageModel(this.togetherAiApi, defaultOptions);

		imageModel.call(new ImagePrompt("prompt", runtimeOptions));

		ArgumentCaptor<TogetherAiApi.GenerateImageRequest> requestCaptor = ArgumentCaptor
			.forClass(TogetherAiApi.GenerateImageRequest.class);
		verify(this.togetherAiApi, times(1)).generateImage(requestCaptor.capture());

		TogetherAiApi.GenerateImageRequest request = requestCaptor.getValue();
		assertThat(request.model()).isEqualTo("runtime-model");
		assertThat(request.steps()).isEqualTo(50);
		assertThat(request.imageUrl()).isEqualTo("https://example.com/runtime.png");
		assertThat(request.seed()).isEqualTo(5678L);
		assertThat(request.n()).isEqualTo(2);
		assertThat(request.height()).isEqualTo(768);
		assertThat(request.width()).isEqualTo(1024);
		assertThat(request.negativePrompt()).isEqualTo("runtime negative prompt");
		assertThat(request.responseFormat()).isEqualTo(TogetherAiImageOptions.ResponseFormat.URL.getValue());
		assertThat(request.guidanceScale()).isEqualTo(14.0f);
		assertThat(request.outputFormat()).isEqualTo(TogetherAiImageOptions.OutputFormat.JPEG.getValue());
		assertThat(request.imageLoras())
			.containsExactly(new TogetherAiApi.GenerateImageRequest.ImageLora("runtime-lora", 1.0f));
		assertThat(request.referenceImages()).containsExactly("https://example.com/runtime-reference.png");
		assertThat(request.disableSafetyChecker()).isTrue();
	}

	@Test
	void shouldUseDefaultOptions_whenRuntimeOptionsAreNull() {
		TogetherAiImageOptions defaultOptions = TogetherAiImageOptions.builder()
			.model("default-model")
			.steps(20)
			.seed(1234L)
			.n(1)
			.height(512)
			.width(512)
			.responseFormat(TogetherAiImageOptions.ResponseFormat.BASE64)
			.guidanceScale(7.0f)
			.outputFormat(TogetherAiImageOptions.OutputFormat.PNG)
			.build();

		TogetherAiImageModel imageModel = new TogetherAiImageModel(this.togetherAiApi, defaultOptions);

		imageModel.call(new ImagePrompt("prompt", null));

		ArgumentCaptor<TogetherAiApi.GenerateImageRequest> requestCaptor = ArgumentCaptor
			.forClass(TogetherAiApi.GenerateImageRequest.class);
		verify(this.togetherAiApi, times(1)).generateImage(requestCaptor.capture());

		TogetherAiApi.GenerateImageRequest request = requestCaptor.getValue();
		assertThat(request.model()).isEqualTo("default-model");
		assertThat(request.steps()).isEqualTo(20);
		assertThat(request.seed()).isEqualTo(1234L);
		assertThat(request.n()).isEqualTo(1);
		assertThat(request.height()).isEqualTo(512);
		assertThat(request.width()).isEqualTo(512);
		assertThat(request.responseFormat()).isEqualTo(TogetherAiImageOptions.ResponseFormat.BASE64.getValue());
		assertThat(request.guidanceScale()).isEqualTo(7.0f);
		assertThat(request.outputFormat()).isEqualTo(TogetherAiImageOptions.OutputFormat.PNG.getValue());
	}

	@Test
	void shouldHandleGenericImageOptionsCorrectly() {
		TogetherAiImageOptions defaultOptions = TogetherAiImageOptions.builder()
			.model("default-model")
			.steps(20)
			.n(1)
			.height(512)
			.width(512)
			.responseFormat(TogetherAiImageOptions.ResponseFormat.BASE64)
			.guidanceScale(7.0f)
			.outputFormat(TogetherAiImageOptions.OutputFormat.PNG)
			.build();

		ImageOptions genericOptions = new ImageOptions() {
			@Override
			public Integer getN() {
				return 2;
			}

			@Override
			public String getModel() {
				return "generic-model";
			}

			@Override
			public Integer getWidth() {
				return 1024;
			}

			@Override
			public Integer getHeight() {
				return 768;
			}

			@Override
			public String getResponseFormat() {
				return "url";
			}

			@Override
			public String getStyle() {
				return null;
			}
		};

		TogetherAiImageModel imageModel = new TogetherAiImageModel(this.togetherAiApi, defaultOptions);

		imageModel.call(new ImagePrompt("prompt", genericOptions));

		ArgumentCaptor<TogetherAiApi.GenerateImageRequest> requestCaptor = ArgumentCaptor
			.forClass(TogetherAiApi.GenerateImageRequest.class);
		verify(this.togetherAiApi, times(1)).generateImage(requestCaptor.capture());

		TogetherAiApi.GenerateImageRequest request = requestCaptor.getValue();
		assertThat(request.model()).isEqualTo("generic-model");
		assertThat(request.n()).isEqualTo(2);
		assertThat(request.width()).isEqualTo(1024);
		assertThat(request.height()).isEqualTo(768);
		assertThat(request.responseFormat()).isEqualTo("url");
		assertThat(request.steps()).isEqualTo(20);
		assertThat(request.guidanceScale()).isEqualTo(7.0f);
		assertThat(request.outputFormat()).isEqualTo(TogetherAiImageOptions.OutputFormat.PNG.getValue());
	}

}
