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
package org.springframework.ai.autoconfigure.vertexai.gemini;

import java.io.IOException;
import java.util.List;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallbackWrapper.Builder.SchemaType;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConditionalOnClass({ VertexAI.class, VertexAiGeminiChatClient.class })
@EnableConfigurationProperties({ VertexAiGeminiChatProperties.class, VertexAiGeminiConnectionProperties.class })
public class VertexAiGeminiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAI vertexAi(VertexAiGeminiConnectionProperties connectionProperties) throws IOException {

		Assert.hasText(connectionProperties.getProjectId(), "Vertex AI project-id must be set!");
		Assert.hasText(connectionProperties.getLocation(), "Vertex AI location must be set!");
		Assert.notNull(connectionProperties.getTransport(), "Vertex AI transport must be set!");

		var vertexAIBuilder = new VertexAI.Builder().setProjectId(connectionProperties.getProjectId())
			.setLocation(connectionProperties.getLocation())
			.setTransport(com.google.cloud.vertexai.Transport.valueOf(connectionProperties.getTransport().name()));

		if (StringUtils.hasText(connectionProperties.getApiEndpoint())) {
			vertexAIBuilder.setApiEndpoint(connectionProperties.getApiEndpoint());
		}
		if (!CollectionUtils.isEmpty(connectionProperties.getScopes())) {
			vertexAIBuilder.setScopes(connectionProperties.getScopes());
		}

		if (connectionProperties.getCredentialsUri() != null) {
			GoogleCredentials credentials = GoogleCredentials
				.fromStream(connectionProperties.getCredentialsUri().getInputStream());

			vertexAIBuilder.setCredentials(credentials);
		}
		return vertexAIBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public VertexAiGeminiChatClient vertexAiGeminiChat(VertexAI vertexAi, VertexAiGeminiChatProperties chatProperties,
			List<FunctionCallback> toolFunctionCallbacks, ApplicationContext context) {

		FunctionCallbackContext functionCallbackContext = springAiFunctionManager(context);

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		return new VertexAiGeminiChatClient(vertexAi, chatProperties.getOptions(), functionCallbackContext);
	}

	/**
	 * Because of the OPEN_API_SCHEMA type, the FunctionCallbackContext instance must
	 * different from the other JSON schema types.
	 */
	private FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setSchemaType(SchemaType.OPEN_API_SCHEMA);
		manager.setApplicationContext(context);
		return manager;
	}

}
