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

package org.springframework.ai.bedrock.converse.experiments;

import java.util.Objects;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Used for reverse engineering the protocol.
 *
 * @author Venkatraman S
 * @since 1.0.0
 */

public final class BedrockConverseGuardrailMain {

	private BedrockConverseGuardrailMain() {
	}

	public static void main(String[] args) {

		String guardrailId = System.getenv("GUARDRAIL_ID");
		String guardrailVersion = System.getenv("GUARDRAIL_VERSION");

		if (guardrailId == null || guardrailVersion == null) {
			System.out.println("GUARDRAIL_ID and GUARDRAIL_VERSION environment variables must be set.");
			System.exit(1);
		}

		BedrockProxyChatModel chatModel = BedrockProxyChatModel.builder()
			.credentialsProvider(EnvironmentVariableCredentialsProvider.create())
			.region(Region.US_EAST_1)
			.build();

		BedrockChatOptions options = BedrockChatOptions.builder()
			.model("google.gemma-3-12b-it")
			.guardrailId(guardrailId)
			.guardrailVersion(guardrailVersion)
			.build();

		// Test 1: Prompt that should pass the guardrail
		System.out.println("Test 1: Prompt that should pass the guardrail");
		ChatResponse allowedResponse = chatModel.call(new Prompt("What is the capital of France?", options));
		System.out.println(Objects.requireNonNull(allowedResponse.getResult()).getOutput().getText());

		// Test 2: Prompt that should not pass the guardrail
		System.out.println("Test 2: Prompt targeting the blocked topic");
		ChatResponse blockedResponse = chatModel.call(new Prompt("This is my mobile number: 123456789, call me!", options));
		System.out.println(Objects.requireNonNull(blockedResponse.getResult()).getOutput().getText());
	}

}
