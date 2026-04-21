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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.memory.repository.bedrock.agentcore.BedrockAgentCoreChatMemoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for
 * {@link BedrockAgentCoreChatMemoryRepositoryAutoConfiguration}.
 *
 * @author Chaemin Lee
 */
@ConfigurationProperties(BedrockAgentCoreChatMemoryRepositoryProperties.CONFIG_PREFIX)
public class BedrockAgentCoreChatMemoryRepositoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.bedrock.agent-core.memory";

	/**
	 * The Bedrock AgentCore Memory Store ID (must be pre-created in AWS).
	 */
	private @Nullable String memoryId;

	/**
	 * The actor ID representing this application in Bedrock AgentCore.
	 */
	private String actorId = BedrockAgentCoreChatMemoryConfig.DEFAULT_ACTOR_ID;

	public @Nullable String getMemoryId() {
		return this.memoryId;
	}

	public void setMemoryId(@Nullable String memoryId) {
		this.memoryId = memoryId;
	}

	public String getActorId() {
		return this.actorId;
	}

	public void setActorId(String actorId) {
		this.actorId = actorId;
	}

}
