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
package org.springframework.ai.google.genai.api;

import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.google.genai.GoogleGenAiImageOptions;
import org.springframework.ai.google.genai.api.dto.GeminiContent;
import org.springframework.ai.google.genai.api.dto.GeminiImageRequest;
import org.springframework.ai.google.genai.api.dto.GeminiImageResponse;
import org.springframework.util.MimeType;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Danil Temnikov
 */

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class GoogleGenAiImageApiTest {

	@Mock
	private Client client;

	private GoogleGenAiImageApi api;

	@BeforeEach
	void setUp() {
		this.api = new GoogleGenAiImageApi(client);
	}

	// -------------------------------------------------------------------------
	// Reflection helpers to call private methods
	// -------------------------------------------------------------------------

	private GenerateContentConfig invokeBuildConfig(GeminiImageRequest.GeminiGenerationConfig generationConfig)
			throws Exception {

		Method m = GoogleGenAiImageApi.class.getDeclaredMethod("buildConfig",
				GeminiImageRequest.GeminiGenerationConfig.class);
		m.setAccessible(true);
		return (GenerateContentConfig) m.invoke(api, generationConfig);
	}

	private Content invokeToSdkContent(List<GeminiContent> contents) throws Exception {
		Method m = GoogleGenAiImageApi.class.getDeclaredMethod("toSdkContent", List.class);
		m.setAccessible(true);
		return (Content) m.invoke(api, contents);
	}

	private GeminiImageResponse invokeFromSdkResponse(GenerateContentResponse response) throws Exception {
		Method m = GoogleGenAiImageApi.class.getDeclaredMethod("fromSdkResponse", GenerateContentResponse.class);
		m.setAccessible(true);
		return (GeminiImageResponse) m.invoke(api, response);
	}

	// -------------------------------------------------------------------------
	// buildConfig tests
	// -------------------------------------------------------------------------

	@Test
	void buildConfigWithNullGenerationConfigProducesDefaultConfig() throws Exception {
		GenerateContentConfig config = invokeBuildConfig(null);

		assertThat(config).isNotNull();
		assertThat(config.responseModalities()).isEmpty();
		assertThat(config.imageConfig().isPresent()).isFalse();
	}

	@Test
	void buildConfigCopiesModalitiesAndImageConfig() throws Exception {
		GeminiImageRequest.GeminiImageConfig imageConfig = new GeminiImageRequest.GeminiImageConfig(
				GoogleGenAiImageOptions.ImageConfig.builder().aspectRatio("16:9").imageSize("1024x1024").build());

		GeminiImageRequest.GeminiGenerationConfig genConfig = new GeminiImageRequest.GeminiGenerationConfig(
				List.of("IMAGE", "TEXT"), imageConfig);

		GenerateContentConfig config = invokeBuildConfig(genConfig);

		assertThat(config.imageConfig().isPresent()).isTrue();
		assertThat(config.imageConfig().get().aspectRatio().orElse(null)).isEqualTo("16:9");
		assertThat(config.imageConfig().get().imageSize().orElse(null)).isEqualTo("1024x1024");
	}

	// -------------------------------------------------------------------------
	// toSdkContent tests
	// -------------------------------------------------------------------------

	@Test
	void toSdkContentWithNullContentsReturnsEmptyContent() throws Exception {
		Content sdkContent = invokeToSdkContent(null);

		assertThat(sdkContent).isNotNull();
		assertThat(sdkContent.parts().get()).isEmpty();
	}

	@Test
	void toSdkContentMapsTextParts() throws Exception {
		GeminiContent.GeminiPartText textPart = new GeminiContent.GeminiPartText("hello");
		GeminiContent content = new GeminiContent(List.of(textPart));

		Content sdkContent = invokeToSdkContent(List.of(content));

		assertThat(sdkContent.parts()).isNotNull();
		List<Part> parts = sdkContent.parts().get();
		Part part = parts.get(0);
		assertThat(part.text().orElse(null)).isEqualTo("hello");
		assertThat(part.inlineData().isPresent()).isFalse();
	}

	@Test
	void toSdkContentMapsInlineDataParts() throws Exception {
		byte[] bytes = "binary-image".getBytes(StandardCharsets.UTF_8);
		String base64 = Base64.getEncoder().encodeToString(bytes);

		GeminiContent.GeminiPartInlineData.InlineData inlineData = new GeminiContent.GeminiPartInlineData.InlineData(
				MimeType.valueOf("image/png"), base64);
		GeminiContent.GeminiPartInlineData inlinePart = new GeminiContent.GeminiPartInlineData(inlineData);

		GeminiContent content = new GeminiContent(List.of(inlinePart));

		Content sdkContent = invokeToSdkContent(List.of(content));

		assertThat(sdkContent.parts()).isNotNull();
		List<Part> parts = sdkContent.parts().get();
		assertThat(parts).hasSize(1);
		Part part = parts.get(0);

		assertThat(part.text().isPresent()).isFalse();
		assertThat(part.inlineData().isPresent()).isTrue();

		Blob blob = part.inlineData().get();
		assertThat(blob.mimeType().orElse(null)).isEqualTo("image/png");

		byte[] decoded = blob.data().orElse(null);
		assertThat(decoded).isNotNull();
		assertThat(decoded).isEqualTo(bytes);
	}

	// -------------------------------------------------------------------------
	// fromSdkResponse tests
	// -------------------------------------------------------------------------

	@Test
	void fromSdkResponseWithNullResponseYieldsEmptyParts() throws Exception {
		GenerateContentResponse sdkResponse = null;

		GeminiImageResponse response = invokeFromSdkResponse(sdkResponse);

		assertThat(response).isNotNull();
		assertThat(response.getCandidates()).hasSize(1);
		GeminiImageResponse.GeminiCandidate candidate = response.getCandidates().get(0);
		assertThat(candidate.getContent().getParts()).isEmpty();
	}

	@Test
	void fromSdkResponseWithTextPartMapsToGeminiPartText() throws Exception {
		GenerateContentResponse sdkResponse = mock(GenerateContentResponse.class);
		Part partMock = mock(Part.class);

		// text present, no inlineData
		org.mockito.Mockito.when(partMock.text()).thenReturn(Optional.of("hello world"));
		org.mockito.Mockito.when(partMock.inlineData()).thenReturn(Optional.empty());
		org.mockito.Mockito.when(sdkResponse.parts()).thenReturn(ImmutableList.of(partMock));

		GeminiImageResponse response = invokeFromSdkResponse(sdkResponse);

		assertThat(response.getCandidates()).hasSize(1);
		GeminiImageResponse.GeminiCandidate candidate = response.getCandidates().get(0);
		List<GeminiContent.GeminiPart> parts = candidate.getContent().getParts();

		assertThat(parts).hasSize(1);
		assertThat(parts.get(0)).isInstanceOf(GeminiContent.GeminiPartText.class);
		GeminiContent.GeminiPartText textPart = (GeminiContent.GeminiPartText) parts.get(0);
		assertThat(textPart.getText()).isEqualTo("hello world");
	}

	@Test
	void fromSdkResponseWithInlineDataPartMapsToGeminiPartInlineData() throws Exception {
		GenerateContentResponse sdkResponse = mock(GenerateContentResponse.class);
		Part partMock = mock(Part.class);
		Blob blobMock = mock(Blob.class);

		byte[] bytes = "img".getBytes(StandardCharsets.UTF_8);
		String expectedBase64 = Base64.getEncoder().encodeToString(bytes);

		org.mockito.Mockito.when(blobMock.mimeType()).thenReturn(Optional.of("image/jpeg"));
		org.mockito.Mockito.when(blobMock.data()).thenReturn(Optional.of(bytes));

		org.mockito.Mockito.when(partMock.text()).thenReturn(Optional.empty());
		org.mockito.Mockito.when(partMock.inlineData()).thenReturn(Optional.of(blobMock));

		org.mockito.Mockito.when(sdkResponse.parts()).thenReturn(ImmutableList.of(partMock));

		GeminiImageResponse response = invokeFromSdkResponse(sdkResponse);

		assertThat(response.getCandidates()).hasSize(1);
		GeminiImageResponse.GeminiCandidate candidate = response.getCandidates().get(0);
		List<GeminiContent.GeminiPart> parts = candidate.getContent().getParts();

		assertThat(parts).hasSize(1);
		assertThat(parts.get(0)).isInstanceOf(GeminiContent.GeminiPartInlineData.class);

		GeminiContent.GeminiPartInlineData inlinePart = (GeminiContent.GeminiPartInlineData) parts.get(0);
		GeminiContent.GeminiPartInlineData.InlineData inlineData = inlinePart.getInlineData();

		assertThat(inlineData.getMimeType().toString()).isEqualTo("image/jpeg");
		assertThat(inlineData.getData()).isEqualTo(expectedBase64);
	}

	@Test
	void fromSdkResponseWithNoPartsProducesCandidateWithEmptyParts() throws Exception {
		GenerateContentResponse sdkResponse = mock(GenerateContentResponse.class);
		org.mockito.Mockito.when(sdkResponse.parts()).thenReturn(ImmutableList.<Part>builder().build());

		GeminiImageResponse response = invokeFromSdkResponse(sdkResponse);

		assertThat(response.getCandidates()).hasSize(1);
		GeminiImageResponse.GeminiCandidate candidate = response.getCandidates().get(0);
		assertThat(candidate.getContent().getParts()).isEmpty();
	}

}
