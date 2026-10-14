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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.MemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.MessageFilter;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * A {@link BaseAdvisor} that manages conversation history using the
 * {@link SessionService}.
 *
 * <p>
 * On each interaction:
 * <ol>
 * <li>Retrieves the session's event history and prepends it to the prompt messages. Of
 * the system messages stored in the session only the latest one is used (it is the system
 * prompt); all system messages are moved to the front, and exact-text duplicates are sent
 * only once.</li>
 * <li>Appends the current user message to the session, if accepted by the configured
 * {@link MessageFilter}; inside a tool loop, the trailing tool results, always.</li>
 * <li>After the model responds, appends the assistant message(s) to the session; messages
 * rejected by the configured {@link MessageFilter} are skipped, except a reply with tool
 * calls, which is always stored. By default, empty assistant messages (blank text, no
 * tool calls, and no media) are filtered out.</li>
 * <li>Names the events it stored on the request and response context
 * ({@link #REQUEST_EVENT_ID_CONTEXT_KEY}, {@link #RESPONSE_EVENT_IDS_CONTEXT_KEY}), so a
 * caller can correlate what it sent and received with the log.</li>
 * </ol>
 *
 * <p>
 * The turn around those model calls (the session's creation, and compaction once the call
 * has returned) is the {@link SessionTurnAdvisor}'s: register both.
 *
 * <p>
 * The session is identified by the {@link #SESSION_ID_CONTEXT_KEY} value in the advisor
 * context. The key must be present on every request; omitting it throws
 * {@link IllegalStateException} to prevent accidental cross-user session sharing.
 *
 * <p>
 * By default it runs inside the tool-calling loop (order
 * {@code HIGHEST_PRECEDENCE + 1000}), once per round: from the second round on, the
 * loop's prompt carries the turn so far, and only the history before it is prepended. A
 * tool call or tool result left without its counterpart is kept out of the prompt, and
 * tool calls and results are always stored. See the ChatClient integration docs for each
 * of these.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionMemoryAdvisor implements BaseAdvisor, MemoryAdvisor {

	private static final Logger logger = LoggerFactory.getLogger(SessionMemoryAdvisor.class);

	/**
	 * Context key used to pass the session ID into the advisor per-request. Equals
	 * {@link org.springframework.ai.chat.memory.ChatMemory#CONVERSATION_ID} so that this
	 * advisor uses the same context key as the rest of Spring AI's memory API. Set via:
	 * {@code .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "my-session-id"))}
	 */
	public static final String SESSION_ID_CONTEXT_KEY = ChatMemory.CONVERSATION_ID;

	/**
	 * Context key for the metadata of a session the advisor creates on first use. The
	 * value must be a {@code Map<String, Object>}; it is ignored for an existing session.
	 * The agent attributes ({@link SessionAttributes#AGENT_NAME}) label the agent a
	 * session runs; set them once for every call of a {@code ChatClient} with
	 * {@code .defaultAdvisors(a -> a.param(SessionMemoryAdvisor.SESSION_METADATA_CONTEXT_KEY, Map.of(SessionAttributes.AGENT_NAME, "support")))}.
	 * A request that passes its own value replaces that default as a whole, so it
	 * includes the agent attributes too.
	 */
	public static final String SESSION_METADATA_CONTEXT_KEY = "chat_memory_session_metadata";

	/**
	 * Context key for a per-request {@link EventFilter} that is merged over the advisor's
	 * configured filter (see {@link EventFilter#merge(EventFilter)}). The value must be
	 * an {@code EventFilter}. Set via:
	 * {@code .advisors(a -> a.param(SessionMemoryAdvisor.EVENT_FILTER_CONTEXT_KEY, EventFilter.lastN(10)))}
	 */
	public static final String EVENT_FILTER_CONTEXT_KEY = "chat_memory_event_filter_id";

	/**
	 * Context key the advisor sets on the request it forwards, and so on the response:
	 * the id of the event that stores the request's trailing user or tool message; absent
	 * when none was stored. Lets a caller name the message it sent by the id the log
	 * knows it by; inside a tool loop it is the current round's.
	 */
	public static final String REQUEST_EVENT_ID_CONTEXT_KEY = "chat_memory_request_event_id";

	/**
	 * Context key the advisor sets on the response: the ids of the events that store the
	 * reply's assistant messages, in reply order, as a {@code List<String>}; empty when
	 * none was stored. Inside a tool loop it is the current round's. A streamed reply's
	 * chunks do not carry it; subscribe to the session for the stored events instead.
	 */
	public static final String RESPONSE_EVENT_IDS_CONTEXT_KEY = "chat_memory_response_event_ids";

	private final SessionService sessionService;

	private final int order;

	private final Scheduler scheduler;

	private final EventFilter eventFilter;

	private final MessageFilter messageFilter;

	private SessionMemoryAdvisor(Builder builder) {
		this.sessionService = builder.sessionService;
		this.order = builder.order;
		this.scheduler = builder.scheduler;
		this.eventFilter = builder.eventFilter;
		this.messageFilter = builder.messageFilter;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public Scheduler getScheduler() {
		return this.scheduler;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {

		// 1. Resolve the session id (it must be in the context) and find or create the
		// session. Whether the caller may use it is not decided here: an authorizing
		// SessionService (see the security module) checks every read and append.
		String sessionId = SessionContexts.sessionId(request.context());
		SessionContexts.findOrCreate(this.sessionService, sessionId, request.context());

		// 2. Read the history: always the active view, whatever the configured or
		// per-request filter says; the events a compaction covered live on for search
		// only
		EventFilter eventFilter = resolveEventFilter(request.context()).merge(EventFilter.active());
		List<SessionEvent> events = this.sessionService.getEvents(sessionId, eventFilter);
		// A lastN window may leave the system prompt, which is configuration, outside the
		// events read: look it up on its own
		SessionEvent systemPrompt = PromptHistory.latestStoredSystemPrompt(events);
		if (systemPrompt == null && !(eventFilter.window() instanceof EventFilter.Window.All)) {
			systemPrompt = latestStoredSystemPrompt(sessionId);
		}

		// 3. The prompt: the history the request does not carry yet, valid for the model
		List<Message> prompt = PromptHistory.of(events, (systemPrompt != null) ? systemPrompt.getMessage() : null)
			.prompt(request.prompt().getInstructions(), sessionId);

		// 4. Store the request's trailing user message, subject to the message filter, or
		// tool results, always. Skipping only affects persistence, not the prompt. A
		// request without one stores nothing.
		Message userMessage = PromptHistory.lastUserOrToolResponse(request.prompt().getInstructions());
		String requestEventId = null;
		if (userMessage != null && shouldPersist(userMessage, sessionId)) {
			requestEventId = UUID.randomUUID().toString();
			this.sessionService.appendEvent(
					SessionEvent.builder().id(requestEventId).sessionId(sessionId).message(userMessage).build());
		}

		// The forwarded request copies the context, so the id reaches the response too.
		// No
		// null value: the model call advisor copies the context with Map.copyOf
		ChatClientRequest.Builder forwarded = request.mutate()
			.prompt(request.prompt().mutate().messages(prompt).build());
		if (requestEventId != null) {
			forwarded.context(REQUEST_EVENT_ID_CONTEXT_KEY, requestEventId);
		}
		return forwarded.build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
		String sessionId = SessionContexts.sessionId(response.context());

		// 1. Append the assistant message(s) produced by the model, subject to the
		// configured message filter. By default excludes messages that carry no
		// content — blank text, no tool calls, and no media.
		List<String> responseEventIds = new ArrayList<>();
		if (response.chatResponse() != null) {
			response.chatResponse().getResults().stream().map(Generation::getOutput).forEach(reply -> {
				if (!shouldPersist(reply, sessionId)) {
					return;
				}
				String eventId = UUID.randomUUID().toString();
				this.sessionService
					.appendEvent(SessionEvent.builder().id(eventId).sessionId(sessionId).message(reply).build());
				responseEventIds.add(eventId);
			});
		}
		ChatClientResponse named = response.mutate()
			.context(RESPONSE_EVENT_IDS_CONTEXT_KEY, List.copyOf(responseEventIds))
			.build();

		return named;
	}

	/**
	 * Reads the latest stored (non-synthetic, active) system message of the session with
	 * one pushed-down query, for a read whose window may not contain it.
	 */
	private @Nullable SessionEvent latestStoredSystemPrompt(String sessionId) {
		EventFilter latestSystem = EventFilter.builder()
			.messageTypes(Set.of(MessageType.SYSTEM))
			.excludeSynthetic(true)
			.activeView(true)
			.lastN(1)
			.build();
		List<SessionEvent> found = this.sessionService.getEvents(sessionId, latestSystem);
		return found.isEmpty() ? null : found.get(0);
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		return Mono.just(request)
			.publishOn(this.scheduler)
			.map(r -> this.before(r, chain))
			.flatMapMany(chain::nextStream)
			// Re-pin to the scheduler so that the after() callback (which performs
			// synchronous session writes) always runs on the configured scheduler rather
			// than the LLM streaming thread.
			.publishOn(this.scheduler)
			.transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux,
					r -> this.after(r, chain)));
	}

	/**
	 * Returns the advisor's configured filter, merged with the per-request filter from
	 * {@link #EVENT_FILTER_CONTEXT_KEY} when present, so that request-level parameters
	 * override the advisor defaults.
	 */
	private EventFilter resolveEventFilter(Map<String, @Nullable Object> context) {
		Object requestFilterValue = context.get(EVENT_FILTER_CONTEXT_KEY);
		if (requestFilterValue == null) {
			return this.eventFilter;
		}
		if (!(requestFilterValue instanceof EventFilter requestEventFilter)) {
			throw new IllegalArgumentException("Advisor context value for '" + EVENT_FILTER_CONTEXT_KEY
					+ "' must be an EventFilter but was " + requestFilterValue.getClass().getName());
		}
		return this.eventFilter.merge(requestEventFilter);
	}

	/**
	 * Whether the message is stored. A tool call and a tool result always are: either
	 * half alone makes a prompt every provider rejects. The {@link MessageFilter} decides
	 * for every other message.
	 */
	private boolean shouldPersist(Message message, String sessionId) {
		if (message instanceof ToolResponseMessage
				|| (message instanceof AssistantMessage assistant && assistant.hasToolCalls())) {
			return true;
		}
		if (!this.messageFilter.shouldPersist(message)) {
			logger.debug("Skipping [{}] message for session [{}] — rejected by the configured MessageFilter",
					message.getMessageType(), sessionId);
			return false;
		}
		return true;
	}

	public static Builder builder(SessionService sessionService) {
		return new Builder(sessionService);
	}

	public static final class Builder {

		private final SessionService sessionService;

		// Deliberately higher (lower-precedence) than ToolCallingAdvisor's default order
		// (HIGHEST_PRECEDENCE + 300): with ascending order sort, the lower value runs
		// outer, so this places SessionMemoryAdvisor nested inside a default-configured
		// ToolCallingAdvisor's tool-call loop rather than wrapping it -- before()/after()
		// then run once per round, which PromptHistory's overlap with the loop's own
		// prompt is built to tolerate.
		private int order = Ordered.HIGHEST_PRECEDENCE + 1000;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		private EventFilter eventFilter = EventFilter.all();

		private MessageFilter messageFilter = MessageFilter.skipEmptyMessages();

		private Builder(SessionService sessionService) {
			Assert.notNull(sessionService, "sessionService must not be null");
			this.sessionService = sessionService;
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder scheduler(Scheduler scheduler) {
			this.scheduler = scheduler;
			return this;
		}

		/**
		 * Filter applied when loading the session's event history to inject into the
		 * prompt: the active view (always), all of it or its newest
		 * {@link EventFilter#lastN(int) N events}, optionally only some
		 * {@link EventFilter#messageTypes() message types} or without synthetic events.
		 * Defaults to {@link EventFilter#all()}.
		 */
		public Builder eventFilter(EventFilter eventFilter) {
			Assert.notNull(eventFilter, "eventFilter must not be null");
			this.eventFilter = eventFilter;
			return this;
		}

		/**
		 * Filter applied before appending messages to session memory — both the current
		 * user message persisted in {@code before()} and the assistant message(s)
		 * persisted in {@code after()}. Messages the filter rejects are not persisted and
		 * therefore never replayed on later requests. Tool calls and tool results are
		 * always stored, whatever the filter says: to keep them out of the prompt, use an
		 * {@link EventFilter}'s {@code messageTypes} on read. The outgoing prompt is
		 * unaffected. Defaults to {@link MessageFilter#skipEmptyMessages()}.
		 * <p>
		 * Note: replacing the default removes the empty-assistant-message protection (see
		 * issue #19 — some models reject empty messages replayed as history). Compose
		 * instead of replacing when you still want it: <pre>{@code
		 * SessionMemoryAdvisor.builder(sessionService)
		 *     .messageFilter(myFilter.and(MessageFilter.skipEmptyMessages()))
		 *     .build();
		 * }</pre>
		 */
		public Builder messageFilter(MessageFilter messageFilter) {
			Assert.notNull(messageFilter, "messageFilter must not be null");
			this.messageFilter = messageFilter;
			return this;
		}

		public SessionMemoryAdvisor build() {
			return new SessionMemoryAdvisor(this);
		}

	}

}
