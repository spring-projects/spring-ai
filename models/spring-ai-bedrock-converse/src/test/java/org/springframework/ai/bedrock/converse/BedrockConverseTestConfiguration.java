/*
 * Copyright 2023-2024 the original author or authors.
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

import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
public class BedrockConverseTestConfiguration {

	@Bean
	public BedrockProxyChatModel bedrockConverseChatModel() {

		// String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";
		// String modelId = "anthropic.claude-3-5-sonnet-20241022-v2:0";
		// String modelId = "meta.llama3-8b-instruct-v1:0";
		// String modelId = "ai21.jamba-1-5-large-v1:0";
		String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";

		return BedrockProxyChatModel.builder()
			.withCredentialsProvider(EnvironmentVariableCredentialsProvider.create())
			.withRegion(Region.US_EAST_1)
			.withTimeout(Duration.ofSeconds(120))
			// .withRegion(Region.US_EAST_1)
			.withDefaultOptions(FunctionCallingOptions.builder().model(modelId).build())
			.build();
	}

}
