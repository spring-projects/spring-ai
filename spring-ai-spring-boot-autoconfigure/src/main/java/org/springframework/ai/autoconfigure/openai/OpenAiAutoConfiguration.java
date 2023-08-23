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

import com.theokanning.openai.service.OpenAiService;

import org.springframework.ai.autoconfigure.NativeHints;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.ai.openai.client.OpenAiClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.util.StringUtils;

import static org.springframework.ai.autoconfigure.openai.OpenAiProperties.CONFIG_PREFIX;

@AutoConfiguration
@ConditionalOnClass(OpenAiService.class)
@EnableConfigurationProperties(OpenAiProperties.class)
@ImportRuntimeHints(NativeHints.class)
public class OpenAiAutoConfiguration {

	private final OpenAiProperties openAiProperties;

	public OpenAiAutoConfiguration(OpenAiProperties openAiProperties) {
		this.openAiProperties = openAiProperties;
	}

	@Bean
	public OpenAiService theoOpenAiService(OpenAiProperties openAiProperties) {
		if (!StringUtils.hasText(openAiProperties.getApiKey())) {
			throw new IllegalArgumentException(
					"You must provide an API key with the property name " + CONFIG_PREFIX + ".api-key");
		}
		return new OpenAiService(openAiProperties.getApiKey(), openAiProperties.getDuration());
	}

	@Bean
	public OpenAiClient openAiClient(OpenAiProperties openAiProperties, OpenAiService theoOpenAiService) {
		OpenAiClient openAiClient = new OpenAiClient(theoOpenAiService);
		openAiClient.setTemperature(openAiProperties.getTemperature());
		openAiClient.setModel(openAiProperties.getModel());
		return openAiClient;
	}

	@Bean
	public EmbeddingClient openAiEmbeddingClient(OpenAiService theoOpenAiService) {
		return new OpenAiEmbeddingClient(theoOpenAiService);
	}

}
