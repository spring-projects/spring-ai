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

package org.springframework.ai.session.compaction;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ChatClientSummarizer}. The model call is mocked, and the prompts it
 * receives are captured.
 *
 * @author Christian Tzolov
 */
class ChatClientSummarizerTests extends CompactionTestSupport {

	private static final String SUMMARY = "Summary: the user asked about Java and got answers.";

	private static final String SHADOW = ChatClientSummarizer.DEFAULT_SHADOW_PROMPT;

	private ChatClient chatClient;

	private final AtomicReference<String> systemPrompt = new AtomicReference<>();

	private final AtomicReference<String> userPrompt = new AtomicReference<>();

	private final AtomicReference<String> reply = new AtomicReference<>(SUMMARY);

	@BeforeEach
	void setUp() {
		this.chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
		ChatClient.ChatClientRequestSpec afterUser = mock(ChatClient.ChatClientRequestSpec.class,
				Answers.RETURNS_DEEP_STUBS);
		given(afterUser.call().content()).willAnswer(invocation -> this.reply.get());
		given(this.chatClient.prompt().system(anyString())).willAnswer(invocation -> {
			this.systemPrompt.set(invocation.getArgument(0));
			ChatClient.ChatClientRequestSpec afterSystem = mock(ChatClient.ChatClientRequestSpec.class);
			given(afterSystem.user(anyString())).willAnswer(call -> {
				this.userPrompt.set(call.getArgument(0));
				return afterUser;
			});
			return afterSystem;
		});
	}

	private ChatClientSummarizer.Builder summarizer() {
		return ChatClientSummarizer.builder(this.chatClient);
	}

	private static SummaryRequest summaryRequest(List<SessionEvent> priorSummary, List<SessionEvent> covered,
			List<SessionEvent> preserved) {
		return new SummaryRequest(Session.builder().id(SESSION_ID).build(), priorSummary, covered, preserved);
	}

	@Test
	void summaryIsTurnOfShadowPromptAndModelsReply() {
		List<Message> summary = summarizer().build()
			.summarize(summaryRequest(List.of(), userMessages(3), List.of(user("u4"))));

		assertThat(summary).extracting(Message::getMessageType)
			.containsExactly(MessageType.USER, MessageType.ASSISTANT);
		assertThat(summary).extracting(Message::getText).containsExactly(SHADOW, SUMMARY);
		assertThat(this.systemPrompt.get()).contains("conversation summarizer");
	}

	@Test
	void customShadowAndSystemPromptsAreUsed() {
		String shadow = "Bitte fasse unser bisheriges Gespräch zusammen.";

		List<Message> summary = summarizer().shadowPrompt(shadow)
			.systemPrompt("Summarize tersely.")
			.build()
			.summarize(summaryRequest(List.of(), userMessages(3), List.of()));

		assertThat(summary.get(0).getText()).isEqualTo(shadow);
		assertThat(this.systemPrompt.get()).isEqualTo("Summarize tersely.");
	}

	@Test
	void promptHoldsPriorSummaryCoveredEventsAndOverlap() {
		summarizer().overlapSize(2)
			.build()
			.summarize(summaryRequest(summaryTurn("earlier things"), events(turn("u1", "a1")),
					events(turn("u2", "a2"), turn("u3", "a3"))));

		String prompt = this.userPrompt.get();
		assertThat(prompt).contains("=== PRIOR SUMMARY ===", "earlier things").doesNotContain(SHADOW);
		assertThat(prompt).contains("=== CONVERSATION TO SUMMARIZE ===", "User: u1", "Assistant: a1");
		assertThat(prompt).contains("UPCOMING CONTEXT", "User: u2", "Assistant: a2").doesNotContain("u3");
	}

	@Test
	void overlapOfZeroQuotesNothingAndNoPriorSummaryHasNoSection() {
		summarizer().overlapSize(0)
			.build()
			.summarize(summaryRequest(List.of(), events(turn("u1", "a1")), events(turn("u2", "a2"))));

		assertThat(this.userPrompt.get()).doesNotContain("PRIOR SUMMARY", "UPCOMING CONTEXT", "u2");
	}

	@Test
	void toolCallsAndResultsAppearInPrompt() {
		summarizer().build().summarize(summaryRequest(List.of(), toolTurn("What's the weather?"), List.of()));

		assertThat(this.userPrompt.get()).contains("get_weather", "Paris", "22C");
	}

	@Test
	void customEventFormatterShapesPrompt() {
		summarizer().overlapSize(0)
			.eventFormatter(e -> "CUSTOM[" + e.getMessageType() + "]")
			.build()
			.summarize(summaryRequest(List.of(), userMessages(2), List.of()));

		assertThat(this.userPrompt.get()).contains("CUSTOM[USER]").doesNotContain("msg-1");
	}

	@Test
	void nullOrBlankReplyDeclinesAndCallsFailureCallback() {
		AtomicReference<SummaryRequest> failed = new AtomicReference<>();
		ChatClientSummarizer summarizer = summarizer().onSummarizationFailure(failed::set).build();
		SummaryRequest request = summaryRequest(List.of(), userMessages(3), List.of());

		this.reply.set(null);
		assertThat(summarizer.summarize(request)).isEmpty();
		assertThat(failed.get()).isSameAs(request);

		failed.set(null);
		this.reply.set("   ");
		assertThat(summarizer.summarize(request)).isEmpty();
		assertThat(failed.get()).isSameAs(request);
	}

	@Test
	void failureCallbackIsNotCalledOnSuccess() {
		AtomicReference<SummaryRequest> failed = new AtomicReference<>();

		summarizer().onSummarizationFailure(failed::set)
			.build()
			.summarize(summaryRequest(List.of(), userMessages(3), List.of()));

		assertThat(failed.get()).isNull();
	}

	@Test
	void builderRejectsBadArguments() {
		assertThatIllegalArgumentException().isThrownBy(() -> ChatClientSummarizer.builder(null));
		assertThatIllegalArgumentException().isThrownBy(() -> summarizer().overlapSize(-1));
		assertThatIllegalArgumentException().isThrownBy(() -> summarizer().shadowPrompt(" "));
		assertThatIllegalArgumentException().isThrownBy(() -> summarizer().build().summarize(null));
	}

	@Test
	void windowStrategyWithSummarizerProducesSummaryTurn() {
		WindowCompactionStrategy strategy = WindowCompactionStrategy.builder()
			.lastEvents(3)
			.summarizer(summarizer().build())
			.build();

		CompactionResult result = strategy.compact(request(userMessages(6)));

		assertThat(kept(result)).containsExactly(SHADOW, SUMMARY, "msg-4", "msg-5", "msg-6");
		assertThat(covered(result)).containsExactly("msg-1", "msg-2", "msg-3");
		assertThat(this.userPrompt.get()).contains("User: msg-1", "User: msg-3", "User: msg-4", "User: msg-5")
			.doesNotContain("msg-6");
		assertThat(result.tokensEstimatedSaved()).isNotNegative();
		assertThat(assistant("x")).isNotNull();
	}

}
