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

package org.springframework.ai.azure.openai;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.HttpLogOptions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.test.CurlyBracketEscaper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Soby Chacko
 */
@SpringBootTest(classes = AzureOpenAiChatClientIT.TestConfiguration.class)
@RequiresAzureCredentials
public class AzureOpenAiChatClientIT {

	@Autowired
	private ChatClient chatClient;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

	@Test
	void call() {

		// @formatter:off
		ChatResponse response = this.chatClient.prompt()
				.advisors(new SimpleLoggerAdvisor())
				.system(s -> s.text(this.systemTextResource)
						.param("name", "Bob")
						.param("voice", "pirate"))
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getText()).contains("Blackbeard");
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

		// @formatter:off
		Flux<ChatResponse> chatResponse = this.chatClient
				.prompt()
				.advisors(new SimpleLoggerAdvisor())
				.user(u -> u
						.text("Generate the filmography of 5 movies for Tom Hanks. " + System.lineSeparator()
								+ "{format}")
						.param("format", CurlyBracketEscaper.escapeCurlyBrackets(outputConverter.getFormat())))
				.stream()
				.chatResponse();

		List<ChatResponse> chatResponses = chatResponse.collectList()
				.block()
				.stream()
				.toList();

		String generationTextFromStream = chatResponses
				.stream()
				.map(cr -> cr.getResult().getOutput().getText())
				.filter(Objects::nonNull)
				.collect(Collectors.joining());
		// @formatter:on

		ActorsFilms actorsFilms = outputConverter.convert(generationTextFromStream);

		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void streamingAndImperativeResponsesContainIdenticalRelevantResults() {
		String prompt = "Name all states in the USA and their capitals, add a space followed by a hyphen, then another space between the two. "
				+ "List them with a numerical index. Do not use any abbreviations in state or capitals.";

		// Imperative call
		String rawDataFromImperativeCall = this.chatClient.prompt(prompt).call().content();
		String imperativeStatesData = extractStatesData(rawDataFromImperativeCall);
		String formattedImperativeResponse = formatResponse(imperativeStatesData);

		// Streaming call
		String stitchedResponseFromStream = this.chatClient.prompt(prompt)
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

	record ActorsFilms(String actor, List<String> movies) {

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OpenAIClientBuilder openAIClient() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
				.serviceVersion(OpenAIServiceVersion.V2024_02_15_PREVIEW)
				.httpLogOptions(new HttpLogOptions()
					.setLogLevel(com.azure.core.http.policy.HttpLogDetailLevel.BODY_AND_HEADERS));
		}

		@Bean
		public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClientBuilder openAIClientBuilder) {
			return AzureOpenAiChatModel.builder()
				.openAIClientBuilder(openAIClientBuilder)
				.defaultOptions(AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").maxTokens(1000).build())
				.build();
		}

		@Bean
		public ChatClient chatClient(AzureOpenAiChatModel azureOpenAiChatModel) {
			return ChatClient.builder(azureOpenAiChatModel).build();
		}

	}

}
