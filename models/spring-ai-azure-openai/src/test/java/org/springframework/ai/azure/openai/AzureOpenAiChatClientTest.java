/*
 * Copyright 2023 - 2024 the original author or authors.
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

package org.springframework.ai.azure.openai;

import static com.azure.core.http.policy.HttpLogDetailLevel.BODY_AND_HEADERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.HttpLogOptions;

/**
 * @author Soby Chacko
 */
@SpringBootTest(classes = AzureOpenAiChatClientTest.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
public class AzureOpenAiChatClientTest {

	@Autowired
	private ChatClient chatClient;

	@Test
	void streamingAndImperativeResponsesContainIdenticalRelevantResults() {
		String prompt = "Name all states in the USA and their capitals, add a space followed by a hyphen, then another space between the two. "
				+ "List them with a numerical index. Do not use any abbreviations in state or capitals.";

		// Imperative call
		String rawDataFromImperativeCall = chatClient.prompt(prompt).call().content();
		String imperativeStatesData = extractStatesData(rawDataFromImperativeCall);
		String formattedImperativeResponse = formatResponse(imperativeStatesData);

		// Streaming call
		String stitchedResponseFromStream = chatClient.prompt(prompt)
			.stream()
			.content()
			.collectList()
			.block()
			.stream()
			.collect(Collectors.joining());
		String streamingStatesData = extractStatesData(stitchedResponseFromStream);
		String formattedStreamingResponse = formatResponse(streamingStatesData);

		// Assertions
		assertThat(formattedStreamingResponse).isEqualTo(formattedImperativeResponse);
		assertThat(formattedStreamingResponse).contains("1. Alabama - Montgomery");
		assertThat(formattedStreamingResponse).contains("50. Wyoming - Cheyenne");
		assertThat(formattedStreamingResponse.lines().count()).isEqualTo(50);
	}

	private String extractStatesData(String rawData) {
		int firstStateIndex = rawData.indexOf("1. Alabama - Montgomery");
		String lastAlphabeticalState = "50. Wyoming - Cheyenne";
		int lastStateIndex = rawData.indexOf(lastAlphabeticalState) + lastAlphabeticalState.length();
		return rawData.substring(firstStateIndex, lastStateIndex);
	}

	private String formatResponse(String response) {
		return String.join("\n", Arrays.stream(response.split("\n")).map(String::strip).toArray(String[]::new));
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OpenAIClientBuilder openAIClient() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
				.serviceVersion(OpenAIServiceVersion.V2024_02_15_PREVIEW)
				.httpLogOptions(new HttpLogOptions().setLogLevel(BODY_AND_HEADERS));
		}

		@Bean
		public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClientBuilder openAIClientBuilder) {
			return new AzureOpenAiChatModel(openAIClientBuilder,
					AzureOpenAiChatOptions.builder().withDeploymentName("gpt-4o").withMaxTokens(1000).build());

		}

		@Bean
		public ChatClient chatClient(AzureOpenAiChatModel azureOpenAiChatModel) {
			return ChatClient.builder(azureOpenAiChatModel).build();
		}

	}

}
