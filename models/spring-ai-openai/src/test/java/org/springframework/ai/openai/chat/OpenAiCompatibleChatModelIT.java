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
package org.springframework.ai.openai.chat;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiCompatibleChatModelIT {

	List<Message> conversation = List.of(new SystemMessage("You are a helpful assistant."),
			new UserMessage("Are you familiar with pirates from the Golden Age of Piracy?"),
			new AssistantMessage("Aye, I be well-versed in the legends of the Golden Age of Piracy!"),
			new UserMessage("Tell me about 3 most famous ones."));

	static OpenAiChatOptions forModelName(String modelName) {
		return OpenAiChatOptions.builder().withModel(modelName).build();
	};

	static Stream<ChatModel> openAiCompatibleApis() {
		Stream.Builder<ChatModel> builder = Stream.builder();

		builder.add(new OpenAiChatModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")), forModelName("gpt-3.5-turbo")));

		if (System.getenv("GROQ_API_KEY") != null) {
			builder.add(new OpenAiChatModel(new OpenAiApi("https://api.groq.com/openai", System.getenv("GROQ_API_KEY")),
					forModelName("llama3-8b-8192")));
		}

		if (System.getenv("OPEN_ROUTER_API_KEY") != null) {
			builder.add(new OpenAiChatModel(
					new OpenAiApi("https://openrouter.ai/api", System.getenv("OPEN_ROUTER_API_KEY")),
					forModelName("meta-llama/llama-3-8b-instruct")));
		}

		return builder.build();
	}

	@ParameterizedTest
	@MethodSource("openAiCompatibleApis")
	void chatCompletion(ChatModel chatModel) {
		Prompt prompt = new Prompt(conversation);
		ChatResponse response = chatModel.call(prompt);

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getContent()).contains("Blackbeard");
	}

	@ParameterizedTest
	@MethodSource("openAiCompatibleApis")
	void streamCompletion(StreamingChatModel streamingChatModel) {
		Prompt prompt = new Prompt(conversation);
		Flux<ChatResponse> flux = streamingChatModel.stream(prompt);

		List<ChatResponse> responses = flux.collectList().block();
		assertThat(responses).hasSizeGreaterThan(1);

		String stitchedResponseContent = responses.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		assertThat(stitchedResponseContent).contains("Blackbeard");
	}

}
