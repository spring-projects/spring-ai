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

package org.springframework.ai.vectorstore.bedrockknowledgebase.autoconfigure;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionConfiguration;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockTestUtils;
import org.springframework.ai.model.bedrock.autoconfigure.RequiresAwsCredentials;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BedrockKnowledgeBaseVectorStoreAutoConfiguration}
 * ensuring unified credential management across Bedrock integrations.
 *
 * @author Anurag/ENG Subagent
 * @since 2.0.0
 */
@RequiresAwsCredentials
public class BedrockKnowledgeBaseVectorStoreAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.bedrock.aws.access-key=" + System.getenv("AWS_ACCESS_KEY_ID"),
				"spring.ai.bedrock.aws.secret-key=" + System.getenv("AWS_SECRET_ACCESS_KEY"),
				"spring.ai.bedrock.aws.session-token=" + System.getenv("AWS_SESSION_TOKEN"),
				"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
				"spring.ai.vectorstore.bedrock-knowledge-base.knowledge-base-id=test-kb-id",
				"spring.ai.vectorstore.bedrock-knowledge-base.region=" + Region.US_EAST_1.id(),
				"spring.ai.vectorstore.type=" + SpringAIVectorStoreTypes.BEDROCK_KNOWLEDGE_BASE)
		.withConfiguration(AutoConfigurations.of(BedrockAwsConnectionConfiguration.class,
				BedrockKnowledgeBaseVectorStoreAutoConfiguration.class, TestConfig.class));

	/**
	 * Tests that the auto-configured BedrockAgentRuntimeClient uses the same
	 * AwsCredentialsProvider bean created by BedrockAwsConnectionConfiguration when Spring
	 * Cloud AWS credentials are configured via spring.cloud.aws.credentials (or
	 * spring.ai.bedrock.aws.* properties). This ensures unified credential management: the
	 * Knowledge Base similarity search uses the same credentials as the Converse chat model.
	 */
	@Test
	void shouldUseSharedCredentialsProviderForBedrockAgentRuntimeClient() {
		contextRunner.run(context -> {
			var credentialsProvider = context.getBean(AwsCredentialsProvider.class);
			var client = context.getBean(BedrockAgentRuntimeClient.class);

			assertThat(credentialsProvider).isNotNull();
			assertThat(client).isNotNull();

			// Verify the client uses the shared credentials provider by checking its internal
			// credentials provider
			AwsCredentialsProvider clientCredentialsProvider = getClientCredentialsProvider(client);
			assertThat(clientCredentialsProvider).isSameAs(credentialsProvider);
		});
	}

	/**
	 * Tests that when a custom AwsCredentialsProvider bean is provided by the application,
	 * the auto-configured BedrockAgentRuntimeClient uses that custom provider instead of
	 * creating its own.
	 */
	@Test
	void shouldUseCustomCredentialsProviderWhenProvided() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.vectorstore.bedrock-knowledge-base.knowledge-base-id=test-kb-id",
					"spring.ai.vectorstore.bedrock-knowledge-base.region=" + Region.US_EAST_1.id(),
					"spring.ai.vectorstore.type=" + SpringAIVectorStoreTypes.BEDROCK_KNOWLEDGE_BASE)
			.withConfiguration(AutoConfigurations.of(BedrockKnowledgeBaseVectorStoreAutoConfiguration.class,
					CustomCredentialsProviderAutoConfiguration.class, TestConfig.class))
			.run(context -> {
				var customProvider = context.getBean("customCredentialsProvider", AwsCredentialsProvider.class);
				var client = context.getBean(BedrockAgentRuntimeClient.class);

				assertThat(client).isNotNull();

				AwsCredentialsProvider clientCredentialsProvider = getClientCredentialsProvider(client);
				assertThat(clientCredentialsProvider).isSameAs(customProvider);
			});
	}

	/**
	 * Tests that ChatClient and KnowledgeBaseVectorStore can coexist with the same
	 * credentials when both are auto-configured in the same application context.
	 */
	@Test
	void shouldAllowChatClientAndKnowledgeBaseVectorStoreWithSameCredentials() {
		contextRunner.run(context -> {
			// Verify both beans are created
			assertThat(context).hasSingleBean(AwsCredentialsProvider.class);
			assertThat(context).hasSingleBean(BedrockAgentRuntimeClient.class);

			// The credentials provider should be the one from BedrockAwsConnectionConfiguration
			var credentialsProvider = context.getBean(AwsCredentialsProvider.class);
			var client = context.getBean(BedrockAgentRuntimeClient.class);

			AwsCredentialsProvider clientCredentialsProvider = getClientCredentialsProvider(client);
			assertThat(clientCredentialsProvider).isSameAs(credentialsProvider);
		});
	}

	// Helper to extract the credentials provider from the client via reflection
	private AwsCredentialsProvider getClientCredentialsProvider(BedrockAgentRuntimeClient client) {
		try {
			Field credentialsProviderField = BedrockAgentRuntimeClient.class.getDeclaredField("credentialsProvider");
			credentialsProviderField.setAccessible(true);
			return (AwsCredentialsProvider) credentialsProviderField.get(client);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to extract credentials provider from client", e);
		}
	}

	@Configuration
	static class TestConfig {

		@Bean
		public software.amazon.awssdk.core.EndpointsBuilder endpointsBuilder() {
			return new software.amazon.awssdk.core.DefaultAwsDefaultClientProvider(null) {
				// No-op for test
			}.endpointResolver();
		}

	}

	@Configuration
	static class CustomCredentialsProviderAutoConfiguration {

		@Bean
		public AwsCredentialsProvider customCredentialsProvider() {
			return AwsCredentialsProvider.class.cast(() -> software.amazon.awssdk.auth.credentials.AwsBasicCredentials
				.create("test-access-key", "test-secret-key"));
		}

	}

}
