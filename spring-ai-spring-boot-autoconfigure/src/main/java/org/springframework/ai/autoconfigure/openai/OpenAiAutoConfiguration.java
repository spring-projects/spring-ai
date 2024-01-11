/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.openai;

import org.springframework.ai.autoconfigure.NativeHints;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnClass(OpenAiApi.class)
@EnableConfigurationProperties(OpenAiProperties.class)
@ImportRuntimeHints(NativeHints.class)
public class OpenAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAiApi openAiApi(OpenAiProperties openAiProperties) {
		return new OpenAiApi(openAiProperties.getBaseUrl(), openAiProperties.getApiKey(), RestClient.builder());
	}

	@Bean
	@ConditionalOnMissingBean
	public OpenAiChatClient openAiChatClient(OpenAiApi openAiApi, OpenAiProperties openAiProperties) {
		OpenAiChatClient openAiChatClient = new OpenAiChatClient(openAiApi);
		openAiChatClient.setTemperature(openAiProperties.getTemperature());
		openAiChatClient.setModel(openAiProperties.getModel());

		return openAiChatClient;
	}

	@Bean
	@ConditionalOnMissingBean
	public EmbeddingClient openAiEmbeddingClient(OpenAiApi openAiApi, OpenAiProperties openAiProperties) {
		return new OpenAiEmbeddingClient(openAiApi, openAiProperties.getEmbedding().getModel());
	}

}
