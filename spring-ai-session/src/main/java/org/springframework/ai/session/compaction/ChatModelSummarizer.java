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
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.SessionEvent;
import org.springframework.util.Assert;

/**
 * A {@link CompactionSummarizer} that asks a {@link ChatModel} for a rolling summary of
 * the covered conversation: the prior summary is fed back as context, so each summary
 * builds on its predecessors, and the first {@link Builder#overlapSize overlapSize}
 * preserved events are quoted for continuity. The summary comes back as a turn of two
 * messages, a user "shadow prompt" and the assistant summary, so the model keeps seeing a
 * user/assistant alternation. A failed model call or a null or blank reply declines: it
 * is logged, the {@link Builder#onSummarizationFailure failure callback} is called, and
 * the view stays as it is.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class ChatModelSummarizer implements CompactionSummarizer {

	private static final Logger logger = LoggerFactory.getLogger(ChatModelSummarizer.class);

	/** Default number of preserved events quoted in the prompt for continuity. */
	public static final int DEFAULT_OVERLAP_SIZE = 2;

	/** The synthetic user message that opens each summary turn. */
	public static final String DEFAULT_SHADOW_PROMPT = "Summarize the conversation we had so far.";

	private static final String DEFAULT_SYSTEM_PROMPT = """
			You are a conversation summarizer. Your task is to create a concise summary of the \
			conversation history provided. The summary will replace the original events in the \
			context window and must preserve all key information needed to continue the conversation \
			coherently.

			Guidelines:
			- Preserve key facts, decisions, user preferences, and important outcomes.
			- Note any unresolved questions or pending actions.
			- Write in third-person narrative ("The user asked...", "The assistant explained...").
			- Be concise but complete. Omit filler and repetition.
			- If a prior summary is provided, incorporate it naturally — do not repeat it verbatim.
			""";

	private final ChatModel chatModel;

	@Nullable private final ChatOptions chatOptions;

	private final int overlapSize;

	private final String systemPrompt;

	private final String shadowPrompt;

	private final Function<SessionEvent, String> eventFormatter;

	@Nullable private final Consumer<SummaryRequest> onSummarizationFailure;

	private ChatModelSummarizer(Builder builder) {
		this.chatModel = builder.chatModel;
		this.chatOptions = builder.chatOptions;
		this.overlapSize = builder.overlapSize;
		this.systemPrompt = builder.systemPrompt;
		this.shadowPrompt = builder.shadowPrompt;
		this.eventFormatter = builder.eventFormatter;
		this.onSummarizationFailure = builder.onSummarizationFailure;
	}

	public static Builder builder(ChatModel chatModel) {
		return new Builder(chatModel);
	}

	@Override
	public List<Message> summarize(SummaryRequest request) {
		Assert.notNull(request, "request must not be null");
		List<SessionEvent> overlap = request.preserved()
			.subList(0, Math.min(this.overlapSize, request.preserved().size()));
		String userPrompt = prompt(request.priorSummary(), request.covered(), overlap);
		String summary;
		try {
			List<Message> messages = List.of(new SystemMessage(this.systemPrompt), new UserMessage(userPrompt));
			Generation result = this.chatModel.call(new Prompt(messages, this.chatOptions)).getResult();
			summary = (result != null) ? result.getOutput().getText() : null;
		}
		catch (RuntimeException ex) {
			logger.warn("The summary call failed for session '{}'; the view is unchanged", request.session().id(), ex);
			return declined(request);
		}
		if (summary == null || summary.isBlank()) {
			logger.warn("The model returned a null or blank summary for session '{}'; the view is unchanged",
					request.session().id());
			return declined(request);
		}
		return List.of(new UserMessage(this.shadowPrompt), new AssistantMessage(summary));
	}

	private List<Message> declined(SummaryRequest request) {
		if (this.onSummarizationFailure != null) {
			this.onSummarizationFailure.accept(request);
		}
		return List.of();
	}

	/**
	 * The user prompt: the prior summary (its assistant messages; the shadow prompts are
	 * structure, not content), the events to summarize, and the overlap for continuity.
	 */
	private String prompt(List<SessionEvent> priorSummary, List<SessionEvent> covered, List<SessionEvent> overlap) {
		StringBuilder prompt = new StringBuilder();
		if (!priorSummary.isEmpty()) {
			prompt.append("=== PRIOR SUMMARY ===\n");
			priorSummary.stream()
				.filter(e -> e.hasMessage() && e.getMessageType() != MessageType.USER)
				.forEach(e -> prompt.append(e.getMessage().getText()).append("\n"));
			prompt.append("\n");
		}
		prompt.append("=== CONVERSATION TO SUMMARIZE ===\n");
		covered.forEach(e -> prompt.append(this.eventFormatter.apply(e)).append("\n"));
		if (!overlap.isEmpty()) {
			prompt.append("\n=== UPCOMING CONTEXT (do not summarize — for continuity only) ===\n");
			overlap.forEach(e -> prompt.append(this.eventFormatter.apply(e)).append("\n"));
		}
		prompt.append("\nPlease write the summary now:");
		return prompt.toString();
	}

	public static final class Builder {

		private final ChatModel chatModel;

		@Nullable private ChatOptions chatOptions;

		private int overlapSize = DEFAULT_OVERLAP_SIZE;

		private String systemPrompt = DEFAULT_SYSTEM_PROMPT;

		private String shadowPrompt = DEFAULT_SHADOW_PROMPT;

		private Function<SessionEvent, String> eventFormatter = EventText::of;

		@Nullable private Consumer<SummaryRequest> onSummarizationFailure;

		private Builder(ChatModel chatModel) {
			Assert.notNull(chatModel, "chatModel must not be null");
			this.chatModel = chatModel;
		}

		/**
		 * The options of the summarizing call, a smaller model for example; none by
		 * default.
		 */
		public Builder chatOptions(ChatOptions chatOptions) {
			Assert.notNull(chatOptions, "chatOptions must not be null");
			this.chatOptions = chatOptions;
			return this;
		}

		/**
		 * How many of the preserved events are quoted in the prompt for continuity;
		 * {@value #DEFAULT_OVERLAP_SIZE} by default, {@code 0} for none.
		 */
		public Builder overlapSize(int overlapSize) {
			Assert.isTrue(overlapSize >= 0, "overlapSize must be >= 0");
			this.overlapSize = overlapSize;
			return this;
		}

		/** Replaces the default system prompt of the summarizing call. */
		public Builder systemPrompt(String systemPrompt) {
			Assert.hasText(systemPrompt, "systemPrompt must not be empty");
			this.systemPrompt = systemPrompt;
			return this;
		}

		/**
		 * Replaces the synthetic user message that opens each summary turn;
		 * {@link #DEFAULT_SHADOW_PROMPT} by default. For another language, or an agent
		 * that needs a different framing.
		 */
		public Builder shadowPrompt(String shadowPrompt) {
			Assert.hasText(shadowPrompt, "shadowPrompt must not be empty");
			this.shadowPrompt = shadowPrompt;
			return this;
		}

		/**
		 * How an event is rendered in the prompt; {@link EventText#of(SessionEvent)} by
		 * default.
		 */
		public Builder eventFormatter(Function<SessionEvent, String> eventFormatter) {
			Assert.notNull(eventFormatter, "eventFormatter must not be null");
			this.eventFormatter = eventFormatter;
			return this;
		}

		/**
		 * Called with the request when the model call fails or returns a null or blank
		 * summary, after the warning is logged.
		 */
		public Builder onSummarizationFailure(Consumer<SummaryRequest> onSummarizationFailure) {
			Assert.notNull(onSummarizationFailure, "onSummarizationFailure must not be null");
			this.onSummarizationFailure = onSummarizationFailure;
			return this;
		}

		public ChatModelSummarizer build() {
			return new ChatModelSummarizer(this);
		}

	}

}
