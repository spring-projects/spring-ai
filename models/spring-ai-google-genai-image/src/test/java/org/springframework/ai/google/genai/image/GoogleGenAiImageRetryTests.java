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

package org.springframework.ai.google.genai.image;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Blob;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.content.Media;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Olivier Le Quellec
 */
@ExtendWith(MockitoExtension.class)
public class GoogleGenAiImageRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private Client mockGenAiClient;

	@Mock
	private Models mockModels;

	@Mock
	private GoogleGenAiImageConnectionDetails mockConnectionDetails;

	private GoogleGenAiImageModel imageModel;

	@BeforeEach
	public void setUp() throws Exception {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.setRetryListener(this.retryListener);

		// Create a mock Client and use reflection to set the models field
		this.mockGenAiClient = mock(Client.class);
		Field modelsField = Client.class.getDeclaredField("models");
		modelsField.setAccessible(true);
		modelsField.set(this.mockGenAiClient, this.mockModels);

		// Set up the mock connection details to return the mock client
		given(this.mockConnectionDetails.getGenAiClient()).willReturn(this.mockGenAiClient);
		// Use lenient stubbing: some tests (constructor tests, setObservationConvention,
		// exception-before-call tests) do not reach getModelEndpointName()
		lenient().when(this.mockConnectionDetails.getModelEndpointName(anyString()))
			.thenAnswer(invocation -> invocation.getArgument(0));

		this.imageModel = new GoogleGenAiImageModel(this.mockConnectionDetails,
				GoogleGenAiImageOptions.builder().build(), this.retryTemplate);
	}

	// ======= Retry behaviour tests =======

	@Test
	public void googleGenAiImageTransientError() {
		GenerateContentResponse mockResponse = buildMockResponse();

		// Setup the mock client to throw transient errors then succeed
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(mockResponse);

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		ImageResponse result = this.imageModel
			.call(new ImagePrompt("A light cream colored mini golden doodle", options));

		assertThat(result).isNotNull();
		assertThat(result.getResults()).hasSize(1);
		assertThat(result.getResults().get(0).getOutput()).isNotNull();
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);

		verify(this.mockModels, times(3)).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
	}

	@Test
	public void googleGenAiImageNonTransientError() {
		// Setup the mock client to throw a non-transient error
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willThrow(new RuntimeException("Non Transient Error"));

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		// Assert that a RuntimeException is thrown and not retried
		assertThatThrownBy(
				() -> this.imageModel.call(new ImagePrompt("A light cream colored mini golden doodle", options)))
			.isInstanceOf(RuntimeException.class);

		// Verify that generateContent was called only once (no retries for non-transient
		// errors)
		verify(this.mockModels, times(1)).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
	}

	// ======= Constructor tests =======

	@Test
	public void googleGenAiImageTwoArgConstructor() {
		// Test 2-arg constructor (uses DEFAULT_RETRY_TEMPLATE)
		GoogleGenAiImageConnectionDetails connectionDetails = GoogleGenAiImageConnectionDetails.builder()
			.genAiClient(this.mockGenAiClient)
			.build();

		GoogleGenAiImageModel model = new GoogleGenAiImageModel(connectionDetails,
				GoogleGenAiImageOptions.builder().model("gemini-2.5-flash-image").build());

		assertThat(model).isNotNull();
	}

	@Test
	public void googleGenAiImageFourArgConstructor() {
		// Test 4-arg constructor (with explicit ObservationRegistry)
		GoogleGenAiImageConnectionDetails connectionDetails = GoogleGenAiImageConnectionDetails.builder()
			.genAiClient(this.mockGenAiClient)
			.build();

		GoogleGenAiImageModel model = new GoogleGenAiImageModel(connectionDetails,
				GoogleGenAiImageOptions.builder().model("gemini-2.5-flash-image").build(), this.retryTemplate,
				ObservationRegistry.NOOP);

		assertThat(model).isNotNull();
	}

	// ======= buildImagePrompt tests =======

	@Test
	public void googleGenAiImageWithNullRequestOptions() {
		// Covers the requestOptions == null branch in buildImagePrompt
		GenerateContentResponse mockResponse = buildMockResponse();
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		// ImagePrompt(List) sets options to null
		ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("A golden doodle")));
		ImageResponse result = this.imageModel.call(prompt);

		assertThat(result).isNotNull();
		assertThat(result.getResults()).hasSize(1);
	}

	@Test
	public void googleGenAiImageWithNonGoogleRequestOptions() {
		// Covers the branch: requestOptions != null && !(requestOptions instanceof
		// GoogleGenAiImageOptions)
		GenerateContentResponse mockResponse = buildMockResponse();
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		// Plain ImageOptions (not GoogleGenAiImageOptions) — returns null for all
		// methods by default
		ImageOptions plainOptions = mock(ImageOptions.class);

		ImageResponse result = this.imageModel.call(new ImagePrompt("A golden doodle", plainOptions));

		assertThat(result).isNotNull();
		assertThat(result.getResults()).hasSize(1);
	}

	@Test
	public void googleGenAiImageEmptyModelThrowsException() {
		// GoogleGenAiImageOptions stores "" as-is (not replaced by DEFAULT_MODEL_NAME)
		GoogleGenAiImageConnectionDetails connectionDetails = GoogleGenAiImageConnectionDetails.builder()
			.genAiClient(this.mockGenAiClient)
			.build();

		GoogleGenAiImageModel modelWithEmptyModel = new GoogleGenAiImageModel(connectionDetails,
				GoogleGenAiImageOptions.builder().model("").build(), this.retryTemplate);

		// No options on the prompt → requestOptions == null → mergedOptions uses
		// this.options whose model is ""
		assertThatThrownBy(() -> modelWithEmptyModel.call(new ImagePrompt(List.of(new ImageMessage("text")))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("model cannot be null or empty");
	}

	// ======= call() method tests =======

	@Test
	public void googleGenAiImageEmptyContentsThrowsException() {
		var options = GoogleGenAiImageOptions.builder().model("model").build();

		// ImageMessage with empty text and no media produces zero Parts → contents empty
		assertThatThrownBy(() -> this.imageModel.call(new ImagePrompt(List.of(new ImageMessage("")), options)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ImagePrompt must contain at least one non-empty message");
	}

	@Test
	public void googleGenAiImageWithEmptyCandidatesResponse() {
		// Covers the orElse(List.of()) path when candidates() is Optional.empty()
		GenerateContentResponse mockResponse = mock(GenerateContentResponse.class);
		given(mockResponse.candidates()).willReturn(Optional.empty());

		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		ImageResponse result = this.imageModel.call(new ImagePrompt("A golden doodle", options));

		assertThat(result).isNotNull();
		assertThat(result.getResults()).isEmpty();
	}

	// ======= getGenerateContentConfig tests =======

	@Test
	public void googleGenAiImageWithAllConfigOptions() {
		// Covers n, seed, temperature, topP, topK, maxOutputTokens, labels and
		// safetyFilterLevel != UNSPECIFIED branches in getGenerateContentConfig, and
		// also exercises buildSafetySettings
		GenerateContentResponse mockResponse = buildMockResponse();
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		var options = GoogleGenAiImageOptions.builder()
			.model("model")
			.n(2)
			.seed(42)
			.temperature(0.8f)
			.topP(0.9f)
			.topK(40.0f)
			.maxOutputTokens(1024)
			.labels(Map.of("billing-key", "billing-value"))
			.safetyFilterLevel(GoogleGenAiImageOptions.SafetyFilterLevel.BLOCK_LOW_AND_ABOVE)
			.build();

		ImageResponse result = this.imageModel.call(new ImagePrompt("A golden doodle", options));

		assertThat(result).isNotNull();
		assertThat(result.getResults()).hasSize(1);
		verify(this.mockModels, times(1)).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
	}

	@Test
	public void googleGenAiImageWithSafetyFilterUnspecified() {
		// Covers the safetyFilterLevel == UNSPECIFIED branch (skips buildSafetySettings)
		GenerateContentResponse mockResponse = buildMockResponse();
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		var options = GoogleGenAiImageOptions.builder()
			.model("model")
			.safetyFilterLevel(GoogleGenAiImageOptions.SafetyFilterLevel.SAFETY_FILTER_LEVEL_UNSPECIFIED)
			.build();

		ImageResponse result = this.imageModel.call(new ImagePrompt("A golden doodle", options));

		assertThat(result).isNotNull();
		verify(this.mockModels, times(1)).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
	}

	// ======= getImageConfigBuilder tests =======

	@Test
	public void googleGenAiImageWithImageConfigOptions() {
		// Covers aspectRatio, imageSize, personGeneration, outputMimeType and
		// outputCompressionQuality branches in getImageConfigBuilder
		GenerateContentResponse mockResponse = buildMockResponse();
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		var options = GoogleGenAiImageOptions.builder()
			.model("model")
			.aspectRatio("16:9")
			.imageSize("1K")
			.personGeneration(GoogleGenAiImageOptions.PersonGeneration.ALLOW_ADULT)
			.outputMimeType("image/png")
			.outputCompressionQuality(85)
			.build();

		ImageResponse result = this.imageModel.call(new ImagePrompt("A golden doodle", options));

		assertThat(result).isNotNull();
		verify(this.mockModels, times(1)).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
	}

	// ======= mediaToParts / messageToParts tests =======

	@Test
	public void googleGenAiImageWithBytesMedia() {
		// Covers byte[] branch in mediaToParts and the media-only path in messageToParts
		GenerateContentResponse mockResponse = buildMockResponse();
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		Media bytesMedia = Media.builder().mimeType(MimeType.valueOf("image/png")).data(new byte[] { 1, 2, 3 }).build();

		// Empty text → only media parts; non-empty media → content included
		ImageMessage messageWithMedia = new ImageMessage("", null, List.of(bytesMedia), Map.of());

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		ImageResponse result = this.imageModel.call(new ImagePrompt(List.of(messageWithMedia), options));

		assertThat(result).isNotNull();
		verify(this.mockModels, times(1)).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
	}

	@Test
	public void googleGenAiImageWithUriMedia() {
		// Covers URI branch in mediaToParts (data(Object) stores the URI object as-is)
		GenerateContentResponse mockResponse = buildMockResponse();
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		// Cast to Object so Builder.data(Object) is chosen, preserving the URI instance
		Media uriMedia = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data((Object) URI.create("https://example.com/image.png"))
			.build();

		ImageMessage messageWithMedia = new ImageMessage("", null, List.of(uriMedia), Map.of());

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		ImageResponse result = this.imageModel.call(new ImagePrompt(List.of(messageWithMedia), options));

		assertThat(result).isNotNull();
		verify(this.mockModels, times(1)).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
	}

	@Test
	public void googleGenAiImageWithStringUriMedia() {
		// Covers String branch in mediaToParts
		GenerateContentResponse mockResponse = buildMockResponse();
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		Media stringMedia = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data("https://example.com/image.png")
			.build();

		ImageMessage messageWithMedia = new ImageMessage("", null, List.of(stringMedia), Map.of());

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		ImageResponse result = this.imageModel.call(new ImagePrompt(List.of(messageWithMedia), options));

		assertThat(result).isNotNull();
		verify(this.mockModels, times(1)).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
	}

	@Test
	public void googleGenAiImageWithTextAndMedia() {
		// Covers the text + media path in messageToParts
		GenerateContentResponse mockResponse = buildMockResponse();
		given(this.mockModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
			.willReturn(mockResponse);

		Media bytesMedia = Media.builder().mimeType(MimeType.valueOf("image/png")).data(new byte[] { 4, 5, 6 }).build();

		ImageMessage messageWithTextAndMedia = new ImageMessage("Describe this image", null, List.of(bytesMedia),
				Map.of());

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		ImageResponse result = this.imageModel.call(new ImagePrompt(List.of(messageWithTextAndMedia), options));

		assertThat(result).isNotNull();
		verify(this.mockModels, times(1)).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
	}

	@Test
	public void googleGenAiImageWithUnsupportedMediaTypeThrowsException() {
		// Covers the unsupported-type branch in mediaToParts
		Media unsupportedMedia = Media.builder()
			.mimeType(MimeType.valueOf("image/png"))
			.data(new Object[] { "unsupported-type" }) // Object[] is not byte[], URI, or
														// String
			.build();

		ImageMessage messageWithUnsupportedMedia = new ImageMessage("", null, List.of(unsupportedMedia), Map.of());

		var options = GoogleGenAiImageOptions.builder().model("model").build();
		assertThatThrownBy(() -> this.imageModel.call(new ImagePrompt(List.of(messageWithUnsupportedMedia), options)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported media data type");
	}

	// ======= setObservationConvention test =======

	@Test
	public void googleGenAiImageSetObservationConvention() {
		ImageModelObservationConvention convention = mock(ImageModelObservationConvention.class);
		// Should not throw; the internal field is updated
		this.imageModel.setObservationConvention(convention);
		assertThat(this.imageModel).isNotNull();
	}

	// ======= Helper methods =======

	private GenerateContentResponse buildMockResponse() {
		Blob mockBlob = mock(Blob.class);
		given(mockBlob.data()).willReturn(Optional.of(new byte[] { 1, 2, 3 }));
		given(mockBlob.mimeType()).willReturn(Optional.of("image/png"));

		Part mockPart = mock(Part.class);
		given(mockPart.inlineData()).willReturn(Optional.of(mockBlob));

		Content mockContent = mock(Content.class);
		given(mockContent.parts()).willReturn(Optional.of(List.of(mockPart)));

		Candidate mockCandidate = mock(Candidate.class);
		given(mockCandidate.content()).willReturn(Optional.of(mockContent));

		GenerateContentResponse mockResponse = mock(GenerateContentResponse.class);
		given(mockResponse.candidates()).willReturn(Optional.of(List.of(mockCandidate)));

		return mockResponse;
	}

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public void beforeRetry(final @Nullable RetryPolicy retryPolicy, final @Nullable Retryable<?> retryable) {
			// Count each retry attempt
			this.onErrorRetryCount++;
		}

		@Override
		public void onRetrySuccess(final @Nullable RetryPolicy retryPolicy, final @Nullable Retryable<?> retryable,
				final @Nullable Object result) {
			// Count successful retries — we increment when we succeed after a failure
			this.onSuccessRetryCount++;
		}

	}

}
