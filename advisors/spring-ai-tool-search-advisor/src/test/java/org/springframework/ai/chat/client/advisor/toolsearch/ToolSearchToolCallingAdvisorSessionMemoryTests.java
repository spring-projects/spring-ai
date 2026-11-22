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

package org.springframework.ai.chat.client.advisor.toolsearch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolSearchToolCallingAdvisor} with {@link SessionAdvisors}, driven through a
 * {@link ChatClient} with a scripted model: a tool discovered in an earlier turn and
 * called directly in a later one, from the history, is executed.
 *
 * @author Aref Behboudi
 */
class ToolSearchToolCallingAdvisorSessionMemoryTests {

	private final ScriptedChatModel model = new ScriptedChatModel();

	private final SessionService sessionService = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private final List<String> weatherCalls = new ArrayList<>();

	@Test
	void toolDiscoveredInEarlierTurnIsExecutedWhenCalledDirectly() {
		scriptTwoTurns();
		ChatClient client = client();

		String first = client.prompt()
			.user("Weather in Lisbon?")
			.advisors(SessionAdvisors.session("s1"))
			.call()
			.content();
		String second = client.prompt().user("And Porto?").advisors(SessionAdvisors.session("s1")).call().content();

		assertTwoTurns(first, second);
	}

	@Test
	void streamToolDiscoveredInEarlierTurnIsExecutedWhenCalledDirectly() {
		scriptTwoTurns();
		ChatClient client = client();

		String first = streamContent(client, "Weather in Lisbon?");
		String second = streamContent(client, "And Porto?");

		assertTwoTurns(first, second);
	}

	private void scriptTwoTurns() {
		// Turn 1: search for the tool, then call it. Turn 2: call it directly, as the
		// model sees it in the history.
		this.model.thenCall("c1", "toolSearchTool", "{\"query\":\"weather\"}")
			.thenCall("c2", "get_weather", "{\"city\":\"Lisbon\"}")
			.thenAnswer("Lisbon: sunny.")
			.thenCall("c3", "get_weather", "{\"city\":\"Porto\"}")
			.thenAnswer("Porto: sunny.");
	}

	private void assertTwoTurns(String first, String second) {
		assertThat(first).isEqualTo("Lisbon: sunny.");
		assertThat(second).isEqualTo("Porto: sunny.");
		assertThat(this.weatherCalls).containsExactly("Lisbon", "Porto");

		// The model is offered no more tools than before: in turn 2 it called
		// get_weather although only the tool search tool was offered.
		List<Prompt> prompts = this.model.prompts();
		assertThat(prompts).hasSize(5);
		assertThat(toolNames(prompts.get(0))).containsExactly("toolSearchTool");
		assertThat(toolNames(prompts.get(1))).containsExactlyInAnyOrder("toolSearchTool", "get_weather");
		assertThat(toolNames(prompts.get(3))).containsExactly("toolSearchTool");
	}

	private String streamContent(ChatClient client, String text) {
		return client.prompt()
			.user(text)
			.advisors(SessionAdvisors.session("s1"))
			.stream()
			.content()
			.collect(Collectors.joining())
			.block();
	}

	private ChatClient client() {
		return ChatClient.builder(this.model)
			.defaultAdvisors(SessionAdvisors.builder(this.sessionService).build())
			.defaultAdvisors(ToolSearchToolCallingAdvisor.builder().toolIndex(new RegexToolIndex()).build())
			.defaultTools(FunctionToolCallback.builder("get_weather", (City city) -> {
				this.weatherCalls.add(city.city());
				return "Sunny in " + city.city();
			}).description("The weather in a city").inputType(City.class).build(),
					FunctionToolCallback.builder("get_time", (City city) -> "Noon in " + city.city())
						.description("The local time in a city")
						.inputType(City.class)
						.build())
			.build();
	}

	private static List<String> toolNames(Prompt prompt) {
		return ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks()
			.stream()
			.map(tc -> tc.getToolDefinition().name())
			.toList();
	}

	record City(String city) {
	}

	/**
	 * Answers each prompt with the next scripted reply and keeps the prompts it got.
	 */
	private static class ScriptedChatModel implements ChatModel {

		private final Deque<AssistantMessage> replies = new ArrayDeque<>();

		private final List<Prompt> prompts = new ArrayList<>();

		ScriptedChatModel thenCall(String id, String toolName, String arguments) {
			this.replies.add(AssistantMessage.builder()
				.content("")
				.toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", toolName, arguments)))
				.build());
			return this;
		}

		ScriptedChatModel thenAnswer(String text) {
			this.replies.add(new AssistantMessage(text));
			return this;
		}

		@Override
		public synchronized ChatResponse call(Prompt prompt) {
			this.prompts.add(prompt);
			AssistantMessage reply = this.replies.poll();
			if (reply == null) {
				throw new IllegalStateException("No scripted reply left for prompt " + this.prompts.size());
			}
			return new ChatResponse(List.of(new Generation(reply)));
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			return Flux.defer(() -> Flux.just(call(prompt)));
		}

		List<Prompt> prompts() {
			return this.prompts;
		}

		@Override
		public ChatOptions getDefaultOptions() {
			return ToolCallingChatOptions.builder().build();
		}

		@Override
		public ChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}

	}

}
