/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vertexai.anthropic;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallbackWrapper.Builder.SchemaType;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vertexai.anthropic.VertexAiAnthropicChatModel;
import org.springframework.ai.vertexai.anthropic.api.VertexAiAnthropicApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.List;

/**
 * Auto-configuration for Vertex AI Anthropic Chat.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
@ConditionalOnClass({ VertexAiAnthropicApi.class, VertexAiAnthropicChatModel.class })
@EnableConfigurationProperties({ VertexAiAnthropicChatProperties.class, VertexAiAnthropicConnectionProperties.class })
public class VertexAiAnthropicAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAiAnthropicApi vertexAiAnthropicApi(VertexAiAnthropicConnectionProperties connectionProperties)
			throws IOException {

		Assert.hasText(connectionProperties.getProjectId(), "Vertex AI project-id must be set!");
		Assert.hasText(connectionProperties.getLocation(), "Vertex AI location must be set!");

		VertexAiAnthropicApi.Builder vertexAiAnthropicApiBuilder = new VertexAiAnthropicApi.Builder()
			.projectId(connectionProperties.getProjectId())
			.location(connectionProperties.getLocation());

		if (connectionProperties.getCredentialsUri() != null) {
			GoogleCredentials credentials = GoogleCredentials
				.fromStream(connectionProperties.getCredentialsUri().getInputStream());

			vertexAiAnthropicApiBuilder.credentials(credentials);
		}

		return vertexAiAnthropicApiBuilder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = VertexAiAnthropicChatProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public VertexAiAnthropicChatModel vertexAiAnthropicChat(VertexAiAnthropicApi vertexAiAnthropicApi,
			VertexAiAnthropicChatProperties chatProperties, List<FunctionCallback> toolFunctionCallbacks,
			ApplicationContext context) {

		FunctionCallbackContext functionCallbackContext = springAiFunctionManager(context);

		return new VertexAiAnthropicChatModel(vertexAiAnthropicApi, chatProperties.getOptions(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE, functionCallbackContext, toolFunctionCallbacks);
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
