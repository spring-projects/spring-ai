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
package org.springframework.ai.google.genai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.google.genai.api.GoogleGenAiImageApi;
import org.springframework.ai.google.genai.api.dto.GeminiContent;
import org.springframework.ai.google.genai.api.dto.GeminiImageRequest;
import org.springframework.ai.google.genai.api.dto.GeminiImageResponse;
import org.springframework.ai.image.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Danil Temnikov
 */
class GoogleGenAiImageModelTest {

	private static final String DEFAULT_MODEL = GoogleGenAiImageOptions.Models.GEMINI_2_5_FLASH_IMAGE;

	@Test
	void constructorRejectsNullClient() {
		GoogleGenAiImageOptions defaults = GoogleGenAiImageOptions.builder().model(DEFAULT_MODEL).build();

		assertThrows(NullPointerException.class, () -> new GoogleGenAiImageModel(null, defaults));
	}

	@Test
	void constructorRejectsNullDefaultOptions() {
		GoogleGenAiImageApi api = mock(GoogleGenAiImageApi.class);

		assertThrows(NullPointerException.class, () -> new GoogleGenAiImageModel(api, null));
	}

	@Test
	void constructorRejectsBlankDefaultModel() {
		GoogleGenAiImageApi api = mock(GoogleGenAiImageApi.class);

		GoogleGenAiImageOptions defaults = GoogleGenAiImageOptions.builder().model("").build();

		assertThrows(IllegalArgumentException.class, () -> new GoogleGenAiImageModel(api, defaults));
	}

	@Test
	void callWithGenericImagePromptUsesDefaultModelAndBuildsTextRequest() {
		GoogleGenAiImageApi api = mock(GoogleGenAiImageApi.class);

		GoogleGenAiImageOptions defaults = GoogleGenAiImageOptions.builder().model(DEFAULT_MODEL).build();

		GoogleGenAiImageModel model = new GoogleGenAiImageModel(api, defaults);

		String promptText = "Draw a red cat on the moon";
		ImagePrompt prompt = new ImagePrompt(promptText);

		ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<GeminiImageRequest> requestCaptor = ArgumentCaptor.forClass(GeminiImageRequest.class);

		when(api.generateContent(modelCaptor.capture(), requestCaptor.capture()))
				.thenReturn(new GeminiImageResponse(Collections.emptyList()));

		ImageResponse response = model.call(prompt);
		assertNotNull(response);
		assertNotNull(response.getResults());

		assertEquals(DEFAULT_MODEL, modelCaptor.getValue());

		GeminiImageRequest captured = requestCaptor.getValue();
		assertNotNull(captured);
		List<GeminiContent> contents = captured.getContents();
		assertNotNull(contents);
		assertEquals(1, contents.size());

		GeminiContent content = contents.get(0);
		assertNotNull(content.parts);
		assertEquals(1, content.parts.size());
		assertTrue(content.parts.get(0) instanceof GeminiContent.GeminiPartText);
		GeminiContent.GeminiPartText textPart = (GeminiContent.GeminiPartText) content.parts.get(0);
		assertEquals(promptText, textPart.text);

		GeminiImageRequest.GeminiGenerationConfig genConfig = captured.getGenerationConfig();
		assertNotNull(genConfig);
		assertEquals(Collections.singletonList("IMAGE"), genConfig.getResponseModalities());
	}

	@Test
	void genericImagePromptWithoutInstructionsThrowsException() {
		GoogleGenAiImageApi api = mock(GoogleGenAiImageApi.class);

		GoogleGenAiImageOptions defaults = GoogleGenAiImageOptions.builder().model(DEFAULT_MODEL).build();

		GoogleGenAiImageModel model = new GoogleGenAiImageModel(api, defaults);

		ImagePrompt badPrompt = new ImagePrompt("ignored") {
			@Override
			public List<ImageMessage> getInstructions() {
				return Collections.emptyList();
			}
		};

		assertThrows(IllegalArgumentException.class, () -> model.call(badPrompt));
	}

	@Test
	void genericImagePromptWithMultipleInstructionsThrowsException() {
		GoogleGenAiImageApi api = mock(GoogleGenAiImageApi.class);

		GoogleGenAiImageOptions defaults = GoogleGenAiImageOptions.builder().model(DEFAULT_MODEL).build();

		GoogleGenAiImageModel model = new GoogleGenAiImageModel(api, defaults);

		ImagePrompt badPrompt = new ImagePrompt("ignored") {
			@Override
			public List<ImageMessage> getInstructions() {
				List<ImageMessage> list = new ArrayList<>();
				list.add(new ImageMessage("one"));
				list.add(new ImageMessage("two"));
				return list;
			}
		};

		assertThrows(IllegalArgumentException.class, () -> model.call(badPrompt));
	}

	@Test
	void whenResponseContainsInlineImageItIsMappedToImageGeneration() {
		GoogleGenAiImageApi api = mock(GoogleGenAiImageApi.class);

		GoogleGenAiImageOptions defaults = GoogleGenAiImageOptions.builder().model(DEFAULT_MODEL).build();

		GoogleGenAiImageModel model = new GoogleGenAiImageModel(api, defaults);

		String base64 = "AAA_BASE64";
		GeminiImageResponse response = createSingleInlineImageResponse("image/png", base64);

		when(api.generateContent(anyString(), any(GeminiImageRequest.class))).thenReturn(response);

		ImagePrompt prompt = new ImagePrompt("some text");
		ImageResponse imageResponse = model.call(prompt);

		assertNotNull(imageResponse);
		List<ImageGeneration> gens = imageResponse.getResults();
		assertEquals(1, gens.size());

		ImageGeneration gen = gens.get(0);
		Image image = gen.getOutput();
		assertNotNull(image);
		assertNull(image.getUrl());
		assertEquals(base64, image.getB64Json());
	}

	@Test
	void userNonGeminiOptionsOverrideModelAndNButKeepGeminiSpecificFromDefaults() {
		GoogleGenAiImageApi api = mock(GoogleGenAiImageApi.class);

		GoogleGenAiImageOptions defaults = GoogleGenAiImageOptions.builder()
				.model(DEFAULT_MODEL)
				.imageConfig(GoogleGenAiImageOptions.ImageConfig.builder()
						.aspectRatio(GoogleGenAiImageOptions.AspectRatios.RATIO_1_1)
						.imageSize(GoogleGenAiImageOptions.ImageSizes.SIZE_1K)
						.build())
				.build();

		GoogleGenAiImageModel model = new GoogleGenAiImageModel(api, defaults);

		DummyImageOptions userOptions = new DummyImageOptions("user-model", 5);

		ImagePrompt prompt = new ImagePrompt("text") {
			@Override
			public ImageOptions getOptions() {
				return userOptions;
			}
		};

		ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<GeminiImageRequest> requestCaptor = ArgumentCaptor.forClass(GeminiImageRequest.class);

		when(api.generateContent(modelCaptor.capture(), requestCaptor.capture()))
				.thenReturn(new GeminiImageResponse(Collections.emptyList()));

		model.call(prompt);

		assertEquals("user-model", modelCaptor.getValue());

		GeminiImageRequest req = requestCaptor.getValue();
		assertNotNull(req);
		GeminiImageRequest.GeminiGenerationConfig genConfig = req.getGenerationConfig();
		assertNotNull(genConfig);
		assertNotNull(genConfig.getImageConfig());
		assertEquals(GoogleGenAiImageOptions.AspectRatios.RATIO_1_1, genConfig.getImageConfig().getAspectRatio());
		assertEquals(GoogleGenAiImageOptions.ImageSizes.SIZE_1K, genConfig.getImageConfig().getImageSize());
	}

	@Test
	void whenResponseHasNoCandidatesReturnsEmptyGenerationList() {
		GoogleGenAiImageApi api = mock(GoogleGenAiImageApi.class);

		GoogleGenAiImageOptions defaults = GoogleGenAiImageOptions.builder().model(DEFAULT_MODEL).build();
		GoogleGenAiImageModel model = new GoogleGenAiImageModel(api, defaults);

		when(api.generateContent(anyString(), any(GeminiImageRequest.class)))
				.thenReturn(new GeminiImageResponse(Collections.emptyList()));

		ImagePrompt prompt = new ImagePrompt("something");
		ImageResponse response = model.call(prompt);

		assertNotNull(response);
		assertTrue(response.getResults().isEmpty());
	}

	private static GeminiImageResponse createSingleInlineImageResponse(String mimeType, String base64) {
		GeminiContent.GeminiPartInlineData.InlineData inlineData = new GeminiContent.GeminiPartInlineData.InlineData(
				mimeType, base64);
		GeminiContent.GeminiPartInlineData part = new GeminiContent.GeminiPartInlineData(inlineData);
		GeminiContent content = new GeminiContent(Collections.singletonList(part));
		GeminiImageResponse.GeminiCandidate candidate = new GeminiImageResponse.GeminiCandidate(content);
		return new GeminiImageResponse(Collections.singletonList(candidate));
	}

	private static class DummyImageOptions implements ImageOptions {

		private final String model;

		private final Integer n;

		DummyImageOptions(String model, Integer n) {
			this.model = Objects.requireNonNull(model);
			this.n = n;
		}

		@Override
		public @Nullable Integer getN() {
			return n;
		}

		@Override
		public @Nullable String getModel() {
			return model;
		}

		@Override
		public @Nullable Integer getWidth() {
			return null;
		}

		@Override
		public @Nullable Integer getHeight() {
			return null;
		}

		@Override
		public @Nullable String getResponseFormat() {
			return null;
		}

		@Override
		public @Nullable String getStyle() {
			return null;
		}

	}

}
