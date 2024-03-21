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
package org.springframework.ai.openai.image;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.image.Image;
import org.springframework.ai.openai.api.OpenAiImageApi.*;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class OpenAiImageCreationTests {

	@Mock
	OpenAiImageRequest req;

	@Mock
	OpenAiImageResponse res;

	@Test
	public void urlCreationTest() {
		String testData = "test.url";

		when(req.responseFormat()).thenReturn("url");
		when(res.toString()).thenReturn(testData);

		Image urlImg = tmpImageFactory(req, res);

		assertThat(urlImg).isNotNull();

		assertThat(urlImg.getType()).isEqualTo(OpenAiImageType.URL);
		assertThat(urlImg.getType().getValue()).isEqualTo("url");
		assertThat(urlImg.getData()).isEqualTo(testData);
	}

	@Test
	public void b64CreationTest() {
		String testData = "test.b64";

		when(req.responseFormat()).thenReturn("b64_json");
		when(res.toString()).thenReturn(testData);

		Image b64Img = tmpImageFactory(req, res);

		assertThat(b64Img).isNotNull();

		assertThat(b64Img.getType()).isEqualTo(OpenAiImageType.BASE64);
		assertThat(b64Img.getType().getValue()).isEqualTo("b64_json");
		assertThat(b64Img.getData()).isEqualTo(testData);
	}

	private Image tmpImageFactory(OpenAiImageRequest req, OpenAiImageResponse res) {
		return switch (OpenAiImageType.fromValue(req.responseFormat())) {
			case BASE64 -> new OpenAiBase64Image(res.toString());
			case URL -> new OpenAiUrlImage(res.toString());
		};
	}

}
