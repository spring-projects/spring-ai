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

package org.springframework.ai.chat.memory.repository.bedrock.agentcore;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

import org.springframework.util.Assert;

/**
 * Configuration class for {@link BedrockAgentCoreChatMemoryRepository}.
 *
 * @author Chaemin Lee
 * @see BedrockAgentCoreChatMemoryRepository
 */
public final class BedrockAgentCoreChatMemoryConfig {

	/**
	 * Default actor ID used when no explicit actor is configured.
	 */
	public static final String DEFAULT_ACTOR_ID = "spring-ai";

	private final BedrockAgentCoreClient bedrockAgentCoreClient;

	private final String memoryId;

	private final String actorId;

	private BedrockAgentCoreChatMemoryConfig(Builder builder) {
		Assert.notNull(builder.bedrockAgentCoreClient, "bedrockAgentCoreClient must not be null");
		Assert.hasText(builder.memoryId, "memoryId must not be empty");
		Assert.hasText(builder.actorId, "actorId must not be empty");

		this.bedrockAgentCoreClient = builder.bedrockAgentCoreClient;
		this.memoryId = builder.memoryId;
		this.actorId = builder.actorId;
	}

	public static Builder builder() {
		return new Builder();
	}

	public BedrockAgentCoreClient getBedrockAgentCoreClient() {
		return this.bedrockAgentCoreClient;
	}

	public String getMemoryId() {
		return this.memoryId;
	}

	public String getActorId() {
		return this.actorId;
	}

	/**
	 * Builder for {@link BedrockAgentCoreChatMemoryConfig}.
	 */
	public static final class Builder {

		private @Nullable BedrockAgentCoreClient bedrockAgentCoreClient;

		private @Nullable String memoryId;

		private String actorId = DEFAULT_ACTOR_ID;

		private Builder() {
		}

		public Builder bedrockAgentCoreClient(BedrockAgentCoreClient bedrockAgentCoreClient) {
			this.bedrockAgentCoreClient = bedrockAgentCoreClient;
			return this;
		}

		public Builder memoryId(String memoryId) {
			this.memoryId = memoryId;
			return this;
		}

		public Builder actorId(String actorId) {
			this.actorId = actorId;
			return this;
		}

		public BedrockAgentCoreChatMemoryConfig build() {
			return new BedrockAgentCoreChatMemoryConfig(this);
		}

	}

}
