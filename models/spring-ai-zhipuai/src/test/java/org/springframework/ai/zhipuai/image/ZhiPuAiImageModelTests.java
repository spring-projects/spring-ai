/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.zhipuai.image;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.ZhiPuAiImageModel;
import org.springframework.ai.zhipuai.ZhiPuAiImageOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.spy;

/**
 * @author Yanming Zhou
 */
public class ZhiPuAiImageModelTests {

	@Test
	void defaultModelConfigured() {
		String model = "CogView-3-Flash";
		ZhiPuAiImageApi imageApi = createSpiedImageApi();
		ZhiPuAiImageModel imageModel = new ZhiPuAiImageModel(imageApi,
				ZhiPuAiImageOptions.builder().model(model).build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
		try {
			imageModel.call(new ImagePrompt("something", ImageOptionsBuilder.builder().build()));
		}
		catch (Exception ignored) {

		}
		ArgumentCaptor<ZhiPuAiImageApi.ZhiPuAiImageRequest> argument = ArgumentCaptor
			.forClass(ZhiPuAiImageApi.ZhiPuAiImageRequest.class);
		then(imageApi).should().createImage(argument.capture());
		assertThat(argument.getValue().model()).isEqualTo(model);
	}

	@Test
	void runtimeModelConfigured() {
		String model = "CogView-3-Flash";
		ZhiPuAiImageApi imageApi = createSpiedImageApi();
		ZhiPuAiImageModel imageModel = new ZhiPuAiImageModel(imageApi);
		try {
			imageModel.call(new ImagePrompt("something", ImageOptionsBuilder.builder().model(model).build()));
		}
		catch (Exception ignored) {

		}
		ArgumentCaptor<ZhiPuAiImageApi.ZhiPuAiImageRequest> argument = ArgumentCaptor
			.forClass(ZhiPuAiImageApi.ZhiPuAiImageRequest.class);
		then(imageApi).should().createImage(argument.capture());
		assertThat(argument.getValue().model()).isEqualTo(model);
	}

	@Test
	void runtimeModelOverDefaultModel() {
		String runtimeModel = "CogView-3-Flash";
		String defaultModel = "CogView";
		ZhiPuAiImageApi imageApi = createSpiedImageApi();
		ZhiPuAiImageModel imageModel = new ZhiPuAiImageModel(imageApi,
				ZhiPuAiImageOptions.builder().model(defaultModel).build(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
		try {
			imageModel.call(new ImagePrompt("something", ImageOptionsBuilder.builder().model(runtimeModel).build()));
		}
		catch (Exception ignored) {

		}
		ArgumentCaptor<ZhiPuAiImageApi.ZhiPuAiImageRequest> argument = ArgumentCaptor
			.forClass(ZhiPuAiImageApi.ZhiPuAiImageRequest.class);
		then(imageApi).should().createImage(argument.capture());
		assertThat(argument.getValue().model()).isEqualTo(runtimeModel);
	}

	private ZhiPuAiImageApi createSpiedImageApi() {
		return spy(new ZhiPuAiImageApi("xxx"));
	}

}
