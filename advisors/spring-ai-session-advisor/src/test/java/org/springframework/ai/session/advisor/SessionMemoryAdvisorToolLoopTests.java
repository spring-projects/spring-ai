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

package org.springframework.ai.session.advisor;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionMemoryAdvisor} inside a real {@link ToolCallingAdvisor} loop, driven
 * through a {@link ChatClient} with a scripted model: what each round sends to the model.
 *
 * @author Christian Tzolov
 */
class SessionMemoryAdvisorToolLoopTests {

	private final ScriptedChatModel model = new ScriptedChatModel();

	private final SessionService sessionService = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private final String sessionId = "s1";

	@Test
	void secondTurnWithToolLoopSendsEachMessageOnce() {
		this.model.thenCall("c1", "Lisbon")
			.thenAnswer("Lisbon: sunny.")
			.thenCall("c2", "Porto")
			.thenAnswer("Porto: rain.");
		ChatClient client = client(ToolCallingAdvisor.builder().build());

		say(client, "Weather in Lisbon?");
		say(client, "And Porto?");

		assertThat(this.model.prompts()).hasSize(4).allSatisfy(SessionMemoryAdvisorToolLoopTests::assertNoDuplicates);
		assertThat(ScriptedChatModel.describe(this.model.prompts().get(3))).containsExactly("Weather in Lisbon?",
				"call:c1", "result:c1", "Lisbon: sunny.", "And Porto?", "call:c2", "result:c2");
	}

	@Test
	void toolLoopWithoutItsOwnHistorySendsSameConversation() {
		this.model.thenCall("c1", "Lisbon")
			.thenAnswer("Lisbon: sunny.")
			.thenCall("c2", "Porto")
			.thenAnswer("Porto: rain.");
		ChatClient client = client(ToolCallingAdvisor.builder().conversationHistoryEnabled(false).build());

		say(client, "Weather in Lisbon?");
		say(client, "And Porto?");

		assertThat(this.model.prompts()).hasSize(4).allSatisfy(SessionMemoryAdvisorToolLoopTests::assertNoDuplicates);
		// The loop adds the request's system message to each later round, a blank one
		// when the request has none, which is not sent
		assertThat(ScriptedChatModel.describe(this.model.prompts().get(3))).containsExactly("Weather in Lisbon?",
				"call:c1", "result:c1", "Lisbon: sunny.", "And Porto?", "call:c2", "result:c2");
	}

	private ChatClient client(ToolCallingAdvisor toolCalling) {
		return ChatClient.builder(this.model)
			.defaultAdvisors(SessionTurnAdvisor.builder(this.sessionService).build(), toolCalling,
					SessionMemoryAdvisor.builder(this.sessionService).build())
			.defaultToolCallbacks(FunctionToolCallback.builder("get_weather", (City city) -> "Sunny in " + city.city())
				.description("The weather in a city")
				.inputType(City.class)
				.build())
			.build();
	}

	private String say(ChatClient client, String text) {
		return client.prompt()
			.user(text)
			.advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.call()
			.content();
	}

	private static void assertNoDuplicates(Prompt prompt) {
		assertThat(ScriptedChatModel.describe(prompt)).doesNotHaveDuplicates();
	}

	record City(String city) {
	}

}
