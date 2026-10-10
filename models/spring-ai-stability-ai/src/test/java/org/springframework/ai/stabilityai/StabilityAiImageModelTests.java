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

package org.springframework.ai.stabilityai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for the model resolution of {@link StabilityAiImageModel}.
 */
class StabilityAiImageModelTests {

	private static final String BASE_URL = "https://api.stability.test/v1";

	private static final String RESPONSE = """
			{"result":"success","artifacts":[{"seed":1,"base64":"aW1hZ2U=","finishReason":"SUCCESS"}]}
			""";

	private MockRestServiceServer server;

	private StabilityAiApi stabilityAiApi;

	@BeforeEach
	void setUp() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(restClientBuilder).build();
		this.stabilityAiApi = new StabilityAiApi("test-key", "api-model", BASE_URL, restClientBuilder);
	}

	@Test
	void runtimeModelIsUsedInRequestPath() {
		expectRequestForModel("runtime-model");
		StabilityAiImageModel imageModel = new StabilityAiImageModel(this.stabilityAiApi,
				StabilityAiImageOptions.builder().model("default-model").build());

		imageModel.call(new ImagePrompt("A cat", StabilityAiImageOptions.builder().model("runtime-model").build()));

		this.server.verify();
	}

	@Test
	void defaultModelIsUsedWhenRuntimeOptionsHaveNoModel() {
		expectRequestForModel("default-model");
		StabilityAiImageModel imageModel = new StabilityAiImageModel(this.stabilityAiApi,
				StabilityAiImageOptions.builder().model("default-model").build());

		imageModel.call(new ImagePrompt("A cat", StabilityAiImageOptions.builder().width(512).build()));

		this.server.verify();
	}

	@Test
	void apiModelIsUsedWhenNoOptionsHaveModel() {
		expectRequestForModel("api-model");
		StabilityAiImageModel imageModel = new StabilityAiImageModel(this.stabilityAiApi);

		imageModel.call(new ImagePrompt("A cat"));

		this.server.verify();
	}

	@Test
	void portableRuntimeModelIsUsedInRequestPath() {
		expectRequestForModel("portable-model");
		StabilityAiImageModel imageModel = new StabilityAiImageModel(this.stabilityAiApi);
		ImageOptions runtimeOptions = new ImageOptions() {

			@Override
			public Integer getN() {
				return null;
			}

			@Override
			public String getModel() {
				return "portable-model";
			}

			@Override
			public Integer getWidth() {
				return null;
			}

			@Override
			public Integer getHeight() {
				return null;
			}

			@Override
			public String getResponseFormat() {
				return null;
			}

			@Override
			public String getStyle() {
				return null;
			}

		};

		imageModel.call(new ImagePrompt("A cat", runtimeOptions));

		this.server.verify();
	}

	@Test
	void builderDoesNotApplyDefaultModel() {
		assertThat(StabilityAiImageOptions.builder().build().getModel()).isNull();
	}

	private void expectRequestForModel(String model) {
		this.server.expect(requestTo(BASE_URL + "/generation/" + model + "/text-to-image"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
	}

}
