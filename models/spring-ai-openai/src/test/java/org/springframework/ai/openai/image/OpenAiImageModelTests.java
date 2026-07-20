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

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.images.Image;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImagesResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiImageModel}.
 *
 * @author guan xu
 */
class OpenAiImageModelTests {

	@Test
	void testPropagatesTimeoutFromRequestOptions() {
		Duration expectedTimeout = Duration.ofSeconds(30);

		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		ImagesResponse mockResponse = mock(ImagesResponse.class);
		when(mockResponse.data())
			.thenReturn(Optional.of(List.of(Image.builder().url("https://example.com/image.png").build())));
		when(mockClient.images().generate(any(ImageGenerateParams.class), any(RequestOptions.class)))
			.thenReturn(mockResponse);

		OpenAiImageModel model = OpenAiImageModel.builder().openAiClient(mockClient).build();

		OpenAiImageOptions options = OpenAiImageOptions.builder().timeout(expectedTimeout).build();

		model.call(new ImagePrompt("A small dog", options));

		ArgumentCaptor<RequestOptions> argumentCaptor = ArgumentCaptor.forClass(RequestOptions.class);
		verify(mockClient.images()).generate(any(ImageGenerateParams.class), argumentCaptor.capture());
		RequestOptions value = argumentCaptor.getValue();
		assertThat(value.getTimeout()).isNotNull();
		assertThat(value.getTimeout().request()).isEqualTo(expectedTimeout);
	}

}
