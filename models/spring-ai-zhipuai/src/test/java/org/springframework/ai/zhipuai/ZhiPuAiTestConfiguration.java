/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.zhipuai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author Geng Rong
 */
@SpringBootConfiguration
public class ZhiPuAiTestConfiguration {

	@Bean
	public ZhiPuAiApi zhiPuAiApi() {
		return ZhiPuAiApi.builder().apiKey(getApiKey()).build();
	}

	@Bean
	public ZhiPuAiImageApi zhiPuAiImageApi() {
		return new ZhiPuAiImageApi(getApiKey());
	}

	private String getApiKey() {
		String apiKey = System.getenv("ZHIPU_AI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name ZHIPU_AI_API_KEY");
		}
		return apiKey;
	}

	@Bean
	public ZhiPuAiChatModel zhiPuAiChatModel(ZhiPuAiApi api) {
		return new ZhiPuAiChatModel(api);
	}

	@Bean
	public ZhiPuAiImageModel zhiPuAiImageModel(ZhiPuAiImageApi imageApi) {
		return new ZhiPuAiImageModel(imageApi);
	}

	@Bean
	public EmbeddingModel zhiPuAiEmbeddingModel(ZhiPuAiApi api) {
		return new ZhiPuAiEmbeddingModel(api);
	}

}
