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

package org.springframework.ai.model.tool;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * {@link DefaultToolCallingManager} pausing on tool calls that need a person's
 * confirmation, and carrying out {@link ToolCallDecisions}.
 *
 * @author Christian Tzolov
 */
class DefaultToolCallingManagerDecisionsTests {

	private final List<String> ran = new ArrayList<>();

	private final ToolCallingManager manager = ToolCallingManager.builder().build();

	@Test
	void aCallThatNeedsConfirmationPausesTheBatchBeforeAnythingRuns() {
		ChatResponse reply = reply(toolCall("c1", "weather"), toolCall("c2", "book_flight"));

		assertThatThrownBy(() -> this.manager.executeToolCalls(prompt(), reply)).isInstanceOfSatisfying(
				ToolCallConfirmationRequiredException.class,
				ex -> assertThat(ex.getToolCallIds()).containsExactly("c2"));
		assertThat(this.ran).isEmpty();
	}

	@Test
	void callsThatNeedNoConfirmationRun() {
		ToolExecutionResult result = this.manager.executeToolCalls(prompt(), reply(toolCall("c1", "weather")));

		assertThat(this.ran).containsExactly("weather");
		assertThat(responses(result)).containsExactly("c1=\"sunny\"");
	}

	@Test
	void anAllowedCallRuns() {
		ToolExecutionResult result = this.manager.executeToolCalls(prompt(), reply(toolCall("c1", "book_flight")),
				ToolCallDecisions.of(ToolCallDecision.allow("c1")));

		assertThat(this.ran).containsExactly("book_flight");
		assertThat(responses(result)).containsExactly("c1=\"booked\"");
		// The question, the reply and the results
		assertThat(result.conversationHistory()).hasSize(3);
		assertThat(result.returnDirect()).isFalse();
	}

	@Test
	void aDeniedCallAnswersWithItsMessageWithoutRunning() {
		ToolExecutionResult result = this.manager.executeToolCalls(prompt(), reply(toolCall("c1", "book_flight")),
				ToolCallDecisions.of(ToolCallDecision.deny("c1", "Too expensive.")));

		assertThat(this.ran).isEmpty();
		assertThat(responses(result)).containsExactly("c1=Too expensive.");
		assertThat(result.returnDirect()).isFalse();
	}

	@Test
	void everyCallOfTheReplyNeedsADecision() {
		ChatResponse reply = reply(toolCall("c1", "book_flight"), toolCall("c2", "weather"));

		// The ungated call too: the policy is not re-evaluated when a batch resumes
		assertThatThrownBy(
				() -> this.manager.executeToolCalls(prompt(), reply, ToolCallDecisions.of(ToolCallDecision.deny("c1"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("missing: [c2]");
		assertThat(this.ran).isEmpty();

		ToolExecutionResult result = this.manager.executeToolCalls(prompt(), reply,
				ToolCallDecisions.of(ToolCallDecision.deny("c1"), ToolCallDecision.allow("c2")));
		assertThat(this.ran).containsExactly("weather");
		assertThat(responses(result)).containsExactly("c1=" + ToolCallDecision.DEFAULT_DENY_MESSAGE, "c2=\"sunny\"");
	}

	@Test
	void aDecisionMustBeAboutACallOfTheReply() {
		assertThatThrownBy(() -> this.manager.executeToolCalls(prompt(), reply(toolCall("c1", "book_flight")),
				ToolCallDecisions.of(ToolCallDecision.allow("other"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("'other'");
		assertThat(this.ran).isEmpty();
	}

	@Test
	void anAllowedCallWhoseToolThePromptDoesNotCarryIsRefused() {
		// The resolver would find the tool by name; a decision does not reach that far
		ToolCallingManager resolving = resolving();
		Prompt withoutTools = new Prompt(List.of(new UserMessage("Book VX205")),
				ToolCallingChatOptions.builder().build());

		assertThatThrownBy(() -> resolving.executeToolCalls(withoutTools, reply(toolCall("c1", "book_flight")),
				ToolCallDecisions.of(ToolCallDecision.allow("c1"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("'book_flight'");
		assertThat(this.ran).isEmpty();

		// A denied call runs nothing, so it needs no tool
		ToolExecutionResult result = resolving.executeToolCalls(withoutTools, reply(toolCall("c1", "book_flight")),
				ToolCallDecisions.of(ToolCallDecision.deny("c1")));
		assertThat(responses(result)).containsExactly("c1=" + ToolCallDecision.DEFAULT_DENY_MESSAGE);
	}

	@Test
	void aGatedToolFoundThroughTheResolverPausesToo() {
		Prompt withoutTools = new Prompt(List.of(new UserMessage("Book VX205")),
				ToolCallingChatOptions.builder().build());

		assertThatThrownBy(() -> resolving().executeToolCalls(withoutTools, reply(toolCall("c1", "book_flight"))))
			.isInstanceOf(ToolCallConfirmationRequiredException.class);
		assertThat(this.ran).isEmpty();
	}

	@Test
	void returnDirectOnlyWhenEveryCallRanAndAskedForIt() {
		ToolExecutionResult alone = this.manager.executeToolCalls(prompt(), reply(toolCall("c1", "ticket")),
				ToolCallDecisions.of(ToolCallDecision.allow("c1")));
		assertThat(alone.returnDirect()).isTrue();

		ToolExecutionResult mixed = this.manager.executeToolCalls(prompt(),
				reply(toolCall("c1", "ticket"), toolCall("c2", "book_flight")),
				ToolCallDecisions.of(ToolCallDecision.allow("c1"), ToolCallDecision.deny("c2")));
		assertThat(mixed.returnDirect()).isFalse();
		assertThat(this.ran).containsExactly("ticket", "ticket");
	}

	@Test
	void aPolicyOnTheManagerNamesTheToolsToConfirm() {
		ToolCallingManager naming = ToolCallingManager.builder()
			.toolCallConfirmationPolicy(ToolCallConfirmationPolicy.forTools("weather"))
			.build();

		assertThatThrownBy(() -> naming.executeToolCalls(prompt(), reply(toolCall("c1", "weather"))))
			.isInstanceOf(ToolCallConfirmationRequiredException.class);
		assertThat(this.ran).isEmpty();
	}

	@Test
	void decisionsAreAboutDistinctCalls() {
		assertThatThrownBy(() -> ToolCallDecisions.of(ToolCallDecision.allow("c1"), ToolCallDecision.deny("c1")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aCallWithoutAnIdGetsOneToBeDecidedOn() {
		// Some providers give tool calls no id; the decisions name the calls by id
		ChatResponse reply = reply(toolCall(null, "book_flight"));

		ToolCallConfirmationRequiredException paused = (ToolCallConfirmationRequiredException) catchThrowable(
				() -> this.manager.executeToolCalls(prompt(), reply));

		assertThat(paused).isNotNull();
		String id = paused.getToolCallIds().get(0);
		assertThat(id).isNotBlank();
		assertThat(paused.getToolCallsMessage().getToolCalls()).extracting(AssistantMessage.ToolCall::id)
			.containsExactly(id);

		// The reply the loop returns carries that message, so the decision can name the
		// id
		ToolExecutionResult result = this.manager.executeToolCalls(prompt(), reply(paused.getToolCallsMessage()),
				ToolCallDecisions.of(ToolCallDecision.allow(id)));
		assertThat(responses(result)).containsExactly(id + "=\"booked\"");

		// The model's own copy, without ids, cannot be decided on
		assertThatThrownBy(
				() -> this.manager.executeToolCalls(prompt(), reply, ToolCallDecisions.of(ToolCallDecision.allow(id))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("needs an id of its own");
	}

	@Test
	void callsSharingAnIdGetIdsOfTheirOwn() {
		// One decision would otherwise apply to both calls
		ChatResponse reply = reply(toolCall("c1", "book_flight"), toolCall("c1", "weather"));

		ToolCallConfirmationRequiredException paused = (ToolCallConfirmationRequiredException) catchThrowable(
				() -> this.manager.executeToolCalls(prompt(), reply));

		assertThat(paused).isNotNull();
		List<String> ids = paused.getToolCallsMessage()
			.getToolCalls()
			.stream()
			.map(AssistantMessage.ToolCall::id)
			.toList();
		assertThat(ids).doesNotHaveDuplicates().startsWith("c1");
		assertThat(paused.getToolCallIds()).containsExactly("c1");

		ToolExecutionResult result = this.manager.executeToolCalls(prompt(), reply(paused.getToolCallsMessage()),
				ToolCallDecisions.of(ToolCallDecision.deny("c1"), ToolCallDecision.allow(ids.get(1))));
		assertThat(this.ran).containsExactly("weather");
		assertThat(responses(result)).containsExactly("c1=" + ToolCallDecision.DEFAULT_DENY_MESSAGE,
				ids.get(1) + "=\"sunny\"");

		// The model's copy, with the shared id, cannot be decided on
		assertThatThrownBy(() -> this.manager.executeToolCalls(prompt(), reply,
				ToolCallDecisions.of(ToolCallDecision.allow("c1"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("needs an id of its own");
	}

	@Test
	void aPromptEndingWithTheReplyHoldsItOnceInTheHistory() {
		// An application that holds the whole conversation passes it as the prompt
		AssistantMessage paused = message(toolCall("c1", "book_flight"));
		UserMessage question = new UserMessage("Book VX205");

		ToolExecutionResult result = this.manager.executeToolCalls(prompt(question, paused), reply(paused),
				ToolCallDecisions.of(ToolCallDecision.allow("c1")));

		assertThat(result.conversationHistory()).hasSize(3);
		assertThat(result.conversationHistory().get(1)).isSameAs(paused);
		assertThat(responses(result)).containsExactly("c1=\"booked\"");
	}

	@Test
	void aDenyMessageIsNeverBlank() {
		assertThatThrownBy(() -> ToolCallDecision.deny("c1", " ")).isInstanceOf(IllegalArgumentException.class);
		assertThat(ToolCallDecision.deny("c1").denyMessage()).isEqualTo(ToolCallDecision.DEFAULT_DENY_MESSAGE);
	}

	private ToolCallingManager resolving() {
		return ToolCallingManager.builder()
			.toolCallbackResolver(new StaticToolCallbackResolver(
					List.of(tool("book_flight", "booked", ToolMetadata.builder().requiresConfirmation(true).build()))))
			.resolutionFallbackEnabled(true)
			.build();
	}

	private Prompt prompt() {
		return prompt(new UserMessage("Book VX205"));
	}

	/** A prompt with the messages, carrying the tools. */
	private Prompt prompt(Message... messages) {
		List<ToolCallback> tools = List.of(
				tool("book_flight", "booked", ToolMetadata.builder().requiresConfirmation(true).build()),
				tool("weather", "sunny", ToolMetadata.builder().build()), tool("ticket", "VX205-14C",
						ToolMetadata.builder().requiresConfirmation(true).returnDirect(true).build()));
		return new Prompt(List.of(messages), ToolCallingChatOptions.builder().toolCallbacks(tools).build());
	}

	private ToolCallback tool(String name, String result, ToolMetadata metadata) {
		return FunctionToolCallback.builder(name, (Input input) -> {
			this.ran.add(name);
			return result;
		}).description(name).inputType(Input.class).toolMetadata(metadata).build();
	}

	private static AssistantMessage.ToolCall toolCall(String id, String tool) {
		return new AssistantMessage.ToolCall(id, "function", tool, "{}");
	}

	private static AssistantMessage message(AssistantMessage.ToolCall... toolCalls) {
		return AssistantMessage.builder().content("").toolCalls(List.of(toolCalls)).build();
	}

	private static ChatResponse reply(AssistantMessage message) {
		return new ChatResponse(List.of(new Generation(message)));
	}

	private static ChatResponse reply(AssistantMessage.ToolCall... toolCalls) {
		return reply(message(toolCalls));
	}

	/** "id=data" per tool result the history ends with. */
	private static List<String> responses(ToolExecutionResult result) {
		List<Message> history = result.conversationHistory();
		ToolResponseMessage responses = (ToolResponseMessage) history.get(history.size() - 1);
		return responses.getResponses().stream().map(r -> r.id() + "=" + r.responseData()).toList();
	}

	record Input() {
	}

}
