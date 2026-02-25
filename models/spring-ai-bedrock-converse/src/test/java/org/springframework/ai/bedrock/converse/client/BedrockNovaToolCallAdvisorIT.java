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

package org.springframework.ai.bedrock.converse.client;

import org.junit.jupiter.api.Disabled;

import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.bedrock.converse.RequiresAwsCredentials;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.test.chat.client.advisor.AbstractToolCallAdvisorIT;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for {@link ToolCallAdvisor} functionality with Bedrock SDK.
 *
 * @author Christian Tzolov
 */
@SpringBootTest
@RequiresAwsCredentials
@Disabled
class BedrockNovaToolCallAdvisorIT extends AbstractToolCallAdvisorIT {

	@Override
	protected ChatModel getChatModel() {
		String modelId = "us.amazon.nova-pro-v1:0";

		return BedrockProxyChatModel.builder()
			.defaultOptions(BedrockChatOptions.builder().model(modelId).build())
			.build();
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

	}

}
