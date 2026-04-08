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

package org.springframework.ai.model.chat.memory.repository.bedrock.agentcore.autoconfigure;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.bedrock.agentcore.BedrockAgentCoreChatMemoryConfig;
import org.springframework.ai.chat.memory.repository.bedrock.agentcore.BedrockAgentCoreChatMemoryRepository;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionConfiguration;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.util.Assert;

/**
 * {@link AutoConfiguration} for {@link BedrockAgentCoreChatMemoryRepository}.
 *
 * <p>
 * Requires {@code spring.ai.chat.memory.repository.bedrock.agent-core.memory.memory-id}
 * to be set. AWS credentials and region are resolved via the shared
 * {@link BedrockAwsConnectionConfiguration} (configured via
 * {@code spring.ai.bedrock.aws.*} properties).
 * </p>
 *
 * @author Chaemin Lee
 */
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ BedrockAgentCoreChatMemoryRepository.class, BedrockAgentCoreClient.class })
@EnableConfigurationProperties(BedrockAgentCoreChatMemoryRepositoryProperties.class)
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockAgentCoreChatMemoryRepositoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ChatMemoryRepository.class)
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public BedrockAgentCoreChatMemoryRepository chatMemoryRepository(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockAgentCoreChatMemoryRepositoryProperties props) {
		Assert.hasText(props.getMemoryId(),
				"spring.ai.chat.memory.repository.bedrock.agent-core.memory.memory-id must be set");

		BedrockAgentCoreClient client = BedrockAgentCoreClient.builder()
			.credentialsProvider(credentialsProvider)
			.region(regionProvider.getRegion())
			.build();

		BedrockAgentCoreChatMemoryConfig config = BedrockAgentCoreChatMemoryConfig.builder()
			.bedrockAgentCoreClient(client)
			.memoryId(props.getMemoryId())
			.actorId(props.getActorId())
			.build();

		return new BedrockAgentCoreChatMemoryRepository(config);
	}

}
