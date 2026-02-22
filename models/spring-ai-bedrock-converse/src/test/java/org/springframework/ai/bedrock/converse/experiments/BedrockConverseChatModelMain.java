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

package org.springframework.ai.bedrock.converse.experiments;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Used for reverse engineering the protocol.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */

public final class BedrockConverseChatModelMain {

	private BedrockConverseChatModelMain() {

	}

	public static void main(String[] args) {

		String modelId = "ai21.jamba-1-5-large-v1:0";
		var prompt = new Prompt("Tell me a joke?", ChatOptions.builder().model(modelId).build());

		var chatModel = BedrockProxyChatModel.builder()
			.bedrockRuntimeClient(BedrockRuntimeClient.builder()
				.credentialsProvider(DefaultCredentialsProvider.builder().build())
				.region(Region.US_EAST_1)
				.build())
			.bedrockRuntimeAsyncClient(BedrockRuntimeAsyncClient.builder()
				.credentialsProvider(DefaultCredentialsProvider.builder().build())
				.region(Region.US_EAST_1)
				.build())
			.build();

		var chatResponse = chatModel.call(prompt);
		System.out.println(chatResponse);
	}

}
