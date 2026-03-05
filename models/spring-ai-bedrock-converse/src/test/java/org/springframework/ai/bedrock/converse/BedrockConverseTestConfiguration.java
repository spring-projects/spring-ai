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

package org.springframework.ai.bedrock.converse;

import java.time.Duration;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
public class BedrockConverseTestConfiguration {

	@Bean
	public BedrockProxyChatModel bedrockConverseChatModel() {

		String modelId = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

		return BedrockProxyChatModel.builder()
			.credentialsProvider(EnvironmentVariableCredentialsProvider.create())
			.region(Region.US_EAST_1)
			// .region(Region.US_EAST_1)
			.timeout(Duration.ofSeconds(120))
			.defaultOptions(BedrockChatOptions.builder().model(modelId).build())
			.build();
	}

}
