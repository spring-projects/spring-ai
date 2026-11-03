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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.util.Assert;

/**
 * The end of a session's conversation, read once: the newest message of the active view
 * and what it means for a turn. Every place the {@link SessionTurnAdvisor} reasons about
 * the end of the log goes through it, so the log is read and interpreted in one way, and
 * so do the writes that keep the log whole: a reply never stays in the log without the
 * results of its tool calls, whether they ran, returned directly, were never run, or were
 * lost to a crash.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
final class ConversationTail {

	/** What the conversation ends with. */
	enum Kind {

		/** No conversation yet. */
		EMPTY,

		/** A user message: the turn has not had its first model round. */
		USER_MESSAGE,

		/** Tool results: the loop was between two rounds. */
		TOOL_RESULTS,

		/**
		 * A reply with tool calls and no results after it: a crash, a failed tool or a
		 * lost pause left the calls open.
		 */
		OPEN_REPLY,

		/** A reply without tool calls: the turn answered. */
		REPLY

	}

	// The advisor's category, so its logging configuration keeps applying
	private static final Logger logger = LoggerFactory.getLogger(SessionTurnAdvisor.class);

	private static final EventFilter NEWEST = EventFilter.active().toBuilder().lastN(1).build();

	private final SessionService sessionService;

	private final String sessionId;

	@Nullable private final SessionEvent newest;

	private final Kind kind;

	private ConversationTail(SessionService sessionService, String sessionId, @Nullable SessionEvent newest) {
		this.sessionService = sessionService;
		this.sessionId = sessionId;
		this.newest = newest;
		this.kind = classify(newest);
	}

	/** Reads the tail of the session's conversation: one indexed read. */
	static ConversationTail of(SessionService sessionService, String sessionId) {
		List<SessionEvent> events = sessionService.getEvents(sessionId, NEWEST);
		for (int i = events.size() - 1; i >= 0; i--) {
			SessionEvent event = events.get(i);
			if (event.hasMessage() && event.getMessageType() != MessageType.SYSTEM) {
				return new ConversationTail(sessionService, sessionId, event);
			}
		}
		return new ConversationTail(sessionService, sessionId, null);
	}

	private static Kind classify(@Nullable SessionEvent newest) {
		if (newest == null) {
			return Kind.EMPTY;
		}
		if (newest.hasToolCalls()) {
			return Kind.OPEN_REPLY;
		}
		MessageType type = newest.getMessageType();
		if (type == MessageType.USER) {
			return Kind.USER_MESSAGE;
		}
		if (type == MessageType.TOOL) {
			return Kind.TOOL_RESULTS;
		}
		return Kind.REPLY;
	}

	Kind kind() {
		return this.kind;
	}

	/** Whether the conversation ends with a reply whose tool calls have no results. */
	boolean isOpenReply() {
		return this.kind == Kind.OPEN_REPLY;
	}

	/** The newest message event; for a reply, the stored reply. */
	SessionEvent event() {
		Assert.state(this.newest != null, "the conversation is empty");
		return this.newest;
	}

	/** The stored reply, open or answered. */
	AssistantMessage reply() {
		Assert.state(this.kind == Kind.OPEN_REPLY || this.kind == Kind.REPLY,
				() -> "the conversation does not end with a reply but with " + this.kind);
		return (AssistantMessage) event().getMessage();
	}

	/** The stored reply as a response: what a turn settled on it returns. */
	ChatResponse replyAsResponse() {
		return ChatResponse.builder().generations(List.of(new Generation(reply()))).build();
	}

	/**
	 * Stores the results of the open reply's tool calls after it, once: the id derives
	 * from the reply's, so a second write of the same results is a no-op.
	 */
	void storeResults(ToolResponseMessage results) {
		Assert.state(this.kind == Kind.OPEN_REPLY,
				() -> "no open reply to store results for; the tail is " + this.kind);
		SessionEvent reply = event();
		this.sessionService.appendEvent(SessionEvent.builder()
			.id(reply.getId() + "-results")
			.sessionId(this.sessionId)
			.message(results)
			.build());
	}

	/**
	 * Answers every tool call of the open reply with
	 * {@link SessionTurnAdvisor#UNANSWERED_TOOL_RESULT}, for a turn that ends without a
	 * result for them. Best-effort: a failure is logged.
	 */
	void answerUnresolved() {
		try {
			List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
			for (AssistantMessage.ToolCall call : reply().getToolCalls()) {
				responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(),
						SessionTurnAdvisor.UNANSWERED_TOOL_RESULT));
			}
			storeResults(ToolResponseMessage.builder().responses(responses).build());
		}
		catch (RuntimeException ex) {
			logger.warn("Could not answer the tool calls of session [{}] left without a result: {}", this.sessionId,
					ex.getMessage());
		}
	}

	/**
	 * Stores the results of the open reply's tool calls after a loop that ended without a
	 * model round: the results that came back directly, or, for a breached tool call
	 * limit, the results of the calls that ran, the breach for the call that broke it and
	 * {@link SessionTurnAdvisor#NOT_RUN_TOOL_RESULT} for the calls after it. One result
	 * per call, in the order of the calls. Best-effort: a failure is logged.
	 */
	void storeResults(ChatResponse response) {
		try {
			List<AssistantMessage.ToolCall> calls = reply().getToolCalls();
			List<String> answers = answers(response, calls.size());
			List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
			for (int i = 0; i < calls.size(); i++) {
				responses
					.add(new ToolResponseMessage.ToolResponse(calls.get(i).id(), calls.get(i).name(), answers.get(i)));
			}
			storeResults(ToolResponseMessage.builder().responses(responses).build());
		}
		catch (RuntimeException ex) {
			logger.warn("Could not store the tool results of session [{}]: {}", this.sessionId, ex.getMessage());
		}
	}

	/** One answer per call, in the order of the calls. */
	private static List<String> answers(ChatResponse response, int calls) {
		List<String> answers = new ArrayList<>(calls);
		for (Generation generation : response.getResults()) {
			ChatGenerationMetadata metadata = generation.getMetadata();
			if (ToolExecutionResult.FINISH_REASON.equals(metadata.getFinishReason())) {
				answers.add(Objects.requireNonNullElse(generation.getOutput().getText(), ""));
			}
			else if (ToolCallLimitExceededException.FINISH_REASON.equals(metadata.getFinishReason())) {
				if (metadata
					.get(ToolCallLimitExceededException.METADATA_PARTIAL_TOOL_RESPONSES) instanceof List<?> ran) {
					ran.stream()
						.filter(ToolResponseMessage.ToolResponse.class::isInstance)
						.map(result -> ((ToolResponseMessage.ToolResponse) result).responseData())
						.forEach(answers::add);
				}
				answers.add(Objects.requireNonNullElse(generation.getOutput().getText(), ""));
			}
		}
		while (answers.size() < calls) {
			answers.add(SessionTurnAdvisor.NOT_RUN_TOOL_RESULT);
		}
		return answers.subList(0, calls);
	}

}
