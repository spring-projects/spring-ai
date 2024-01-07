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

package org.springframework.ai.autoconfigure.vertexai.gemini;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import org.springframework.ai.autoconfigure.NativeHints;
import org.springframework.ai.autoconfigure.vertexai.VertexAiEmbeddingProperties;
import org.springframework.ai.vertex.generation.VertexAiGeminiClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.io.IOException;

/**
 * @author Jingzhou Ou
 */
@AutoConfiguration
@ConditionalOnClass(VertexAiGeminiClient.class)
@ImportRuntimeHints(NativeHints.class)
@EnableConfigurationProperties({ GeminiConnectionProperties.class, GeminiChatProperties.class,
		VertexAiEmbeddingProperties.class })
public class GeminiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAiGeminiClient vertexAiGeminiClient(VertexAI vertexAI, GeminiChatProperties geminiChatProperties) {
		GenerationConfig.Builder generationConfigBuilder = GenerationConfig.newBuilder();
		if (geminiChatProperties.getTemperature() != null) {
			generationConfigBuilder.setTemperature(geminiChatProperties.getTemperature());
		}
		if (geminiChatProperties.getMaxOutputTokens() != null) {
			generationConfigBuilder.setMaxOutputTokens(geminiChatProperties.getMaxOutputTokens());
		}
		if (geminiChatProperties.getTopK() != null) {
			generationConfigBuilder.setTopK(geminiChatProperties.getTopK());
		}
		if (geminiChatProperties.getTopP() != null) {
			generationConfigBuilder.setTopP(geminiChatProperties.getTopP());
		}
		GenerationConfig generationConfig = generationConfigBuilder.build();
		return new VertexAiGeminiClient(vertexAI, generationConfig);
	}

	@Bean
	@ConditionalOnMissingBean
	public VertexAI vertexAI(GeminiConnectionProperties geminiConnectionProperties) {
		try {
			return new VertexAI(geminiConnectionProperties.getProjectId(), geminiConnectionProperties.getLocation());
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

}
