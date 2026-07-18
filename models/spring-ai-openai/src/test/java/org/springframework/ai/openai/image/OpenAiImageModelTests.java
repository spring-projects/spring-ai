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

import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImagesResponse;
import com.openai.services.blocking.ImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiImageModel}.
 *
 * @author guan xu
 */
@ExtendWith(MockitoExtension.class)
class OpenAiImageModelTests {

	@Mock
	private OpenAIClient openAiClient;

	@Mock
	private ImageService imageService;

	@Test
	void callRejectsImageResponseWithoutData() {
		when(this.openAiClient.images()).thenReturn(this.imageService);
		when(this.imageService.generate(any(ImageGenerateParams.class)))
			.thenReturn(ImagesResponse.builder().created(1).build());

		OpenAiImageModel imageModel = OpenAiImageModel.builder()
			.openAiClient(this.openAiClient)
			.options(OpenAiImageOptions.builder().model("gpt-image-1").build())
			.build();

		assertThatIllegalArgumentException()
			.isThrownBy(() -> imageModel.call(new ImagePrompt("a duck riding a bicycle")))
			.withMessage("Image generation failed: no image returned");
	}

	@Test
	void callRejectsImageResponseWithEmptyData() {
		when(this.openAiClient.images()).thenReturn(this.imageService);
		when(this.imageService.generate(any(ImageGenerateParams.class)))
			.thenReturn(ImagesResponse.builder().created(1).data(List.of()).build());

		OpenAiImageModel imageModel = OpenAiImageModel.builder()
			.openAiClient(this.openAiClient)
			.options(OpenAiImageOptions.builder().model("gpt-image-1").build())
			.build();

		assertThatIllegalArgumentException()
			.isThrownBy(() -> imageModel.call(new ImagePrompt("a duck riding a bicycle")))
			.withMessage("Image generation failed: no image returned");
	}

}
