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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestAttributesThreadLocalAccessor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the ThreadLocal request context of the subscribing thread survives the
 * thread hop that {@link ToolCallingAdvisor} performs when it executes tool calls during
 * streaming. In streaming mode the tool call loop runs on a
 * {@code Schedulers.boundedElastic()} worker, where the request attributes and the
 * security context would otherwise be missing.
 * <p>
 * {@link ToolCallContextPropagation} is a plain Micrometer bridge over whatever accessors
 * are registered in the {@link ContextRegistry}, so this test registers the request
 * attributes accessor itself - the same step the Spring MVC auto-configuration performs.
 * Spring Security registers its own accessors automatically.
 *
 * @author luyu0425
 */
class ToolCallingAdvisorContextPropagationTests {

	private static final String TOOL_NAME = "testTool";

	private boolean requestAttributesAccessorRegisteredByTest;

	@BeforeEach
	void registerRequestAttributesAccessor() {
		ContextRegistry registry = ContextRegistry.getInstance();

		boolean alreadyRegistered = registry.getThreadLocalAccessors()
			.stream()
			.anyMatch(accessor -> RequestAttributesThreadLocalAccessor.KEY.equals(accessor.key()));

		if (!alreadyRegistered) {
			registry.registerThreadLocalAccessor(new RequestAttributesThreadLocalAccessor());
			this.requestAttributesAccessorRegisteredByTest = true;
		}
	}

	@AfterEach
	void cleanup() {
		RequestContextHolder.resetRequestAttributes();

		// ContextRegistry is a global singleton shared with every other test running in
		// this JVM, so only undo a registration made by this test. Removing an accessor
		// that was already registered would leak into the tests that follow.
		if (this.requestAttributesAccessorRegisteredByTest) {
			ContextRegistry.getInstance().removeThreadLocalAccessor(RequestAttributesThreadLocalAccessor.KEY);
		}
	}

	@Test
	void requestAttributesAreAvailableToToolCallbacksDuringStreaming() {
		RecordingToolCallback toolCallback = new RecordingToolCallback(TOOL_NAME);

		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(ToolCallingManager.builder().build())
			.build();

		ChatClientRequest request = createRequest(toolCallback);

		int[] callCount = { 0 };
		StreamAdvisor terminalAdvisor = new TerminalStreamAdvisor(
				(req, chain) -> Flux.just(callCount[0]++ == 0 ? responseWithToolCall() : finalResponse()));
		// Enter through the chain, the way a ChatClient does, rather than calling the
		// advisor directly: this covers the path the request actually travels.
		StreamAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.<Advisor>of(advisor, terminalAdvisor))
			.build();

		// Simulate the servlet request thread that subscribes to the stream.
		ServletRequestAttributes requestAttributes = new ServletRequestAttributes(new MockHttpServletRequest());
		requestAttributes.setAttribute("tenant", "acme", RequestAttributes.SCOPE_REQUEST);
		RequestContextHolder.setRequestAttributes(requestAttributes);

		String subscribingThreadName = Thread.currentThread().getName();

		List<ChatClientResponse> results = chain.nextStream(request).collectList().block();

		assertThat(results).hasSize(1);
		assertThat(toolCallback.observedAttributes).as("request attributes must be visible to the tool").isNotNull();
		assertThat(toolCallback.observedAttributes.getAttribute("tenant", RequestAttributes.SCOPE_REQUEST))
			.isEqualTo("acme");
		assertThat(toolCallback.threadName).as("the tool must run off the subscribing thread")
			.isNotEqualTo(subscribingThreadName)
			.contains("boundedElastic");
	}

	@Test
	void restoringThreadLocalsIsRevertedWhenTheScopeIsClosed() {
		ServletRequestAttributes requestAttributes = new ServletRequestAttributes(new MockHttpServletRequest());
		requestAttributes.setAttribute("tenant", "acme", RequestAttributes.SCOPE_REQUEST);
		RequestContextHolder.setRequestAttributes(requestAttributes);

		ContextSnapshot snapshot = ToolCallContextPropagation.captureThreadLocals();
		// The capturing thread no longer holds the state, mimicking the boundedElastic
		// worker that executes the tool call.
		RequestContextHolder.resetRequestAttributes();

		try (ContextSnapshot.Scope ignored = ToolCallContextPropagation
			.restoreThreadLocals(snapshot.updateContext(Context.empty()))) {
			RequestAttributes restored = RequestContextHolder.getRequestAttributes();
			assertThat(restored).isNotNull();
			assertThat(restored.getAttribute("tenant", RequestAttributes.SCOPE_REQUEST)).isEqualTo("acme");
		}

		assertThat(RequestContextHolder.getRequestAttributes()).as("the scope must not leak ThreadLocals").isNull();
	}

	@Test
	void missingThreadLocalStateClearsWorkerStateDuringToolExecution() {
		RequestContextHolder.resetRequestAttributes();

		ContextSnapshot snapshot = ToolCallContextPropagation.captureThreadLocals();

		Context context = snapshot.updateContext(Context.empty());

		// A boundedElastic worker is reused across requests, so it may still hold the
		// request attributes of a previous request. The snapshot above carries none, so
		// restoring it has to clear the stale value while the tool executes.
		RequestContextHolder.setRequestAttributes(requestAttributesWithTenant("stale"));

		try (ContextSnapshot.Scope ignored = ToolCallContextPropagation.restoreThreadLocals(context)) {
			assertThat(RequestContextHolder.getRequestAttributes())
				.as("stale worker state must not leak into the tool execution")
				.isNull();
		}

		assertThat(RequestContextHolder.getRequestAttributes())
			.as("the worker's previous state must be restored when the scope closes")
			.isNotNull();
	}

	@Test
	void theObservationThreadLocalIsNotCarriedByTheSnapshot() {
		Observation outer = Observation.start("outer", ObservationRegistry.create());

		ContextSnapshot snapshot;
		try (Observation.Scope ignored = outer.openScope()) {
			snapshot = ToolCallContextPropagation.captureThreadLocals();
		}
		outer.stop();

		Context propagated = snapshot.updateContext(Context.empty());

		// The advisor observations are propagated as an explicit Reactor context value
		// (see DefaultAroundAdvisorChain#nextStream); letting the scope open on the
		// subscribing thread travel with the snapshot would override that value and
		// reparent the advisor observations to it.
		assertThat(propagated.getOrEmpty(ObservationThreadLocalAccessor.KEY))
			.as("the observation of the subscribing thread must not be carried by the snapshot")
			.isEmpty();
	}

	@Test
	void requestAttributesSurviveEveryRoundOfTheToolCallLoop() {
		RecordingToolCallback firstTool = new RecordingToolCallback("firstTool");
		RecordingToolCallback secondTool = new RecordingToolCallback("secondTool");

		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(ToolCallingManager.builder().build())
			.build();

		ChatClientRequest request = createRequest(firstTool, secondTool);

		// The terminal advisor answers the first two rounds with a tool call each and the
		// third one with a final answer, so the recursive loop executes two different
		// tools
		// on two separate boundedElastic hops.
		List<String> plannedToolNames = List.of(firstTool.name, secondTool.name);
		int[] servedRounds = { 0 };
		StreamAdvisor terminalAdvisor = new TerminalStreamAdvisor((req, chain) -> {
			int round = servedRounds[0]++;
			if (round < plannedToolNames.size()) {
				return Flux.just(responseWithToolCall("tool-call-" + (round + 1), plannedToolNames.get(round)));
			}
			return Flux.just(finalResponse());
		});

		StreamAdvisorChain chain = streamChainOf(advisor, terminalAdvisor);

		RequestContextHolder.setRequestAttributes(requestAttributesWithTenant("acme"));

		List<ChatClientResponse> results = chain.nextStream(request).collectList().block();

		assertThat(results).hasSize(1);
		assertThat(servedRounds[0]).as("two tool call rounds and one final round must have run").isEqualTo(3);
		// Each recursion re-enters internalStream() and reads the captured state from the
		// Reactor context of the round before it, so a successful first round alone does
		// not prove the second one still inherits it.
		assertThat(firstTool.observedAttributes).as("the first round must inherit the Reactor context").isNotNull();
		assertThat(firstTool.observedAttributes.getAttribute("tenant", RequestAttributes.SCOPE_REQUEST))
			.isEqualTo("acme");
		assertThat(secondTool.observedAttributes).as("the recursive round must inherit the Reactor context")
			.isNotNull();
		assertThat(secondTool.observedAttributes.getAttribute("tenant", RequestAttributes.SCOPE_REQUEST))
			.isEqualTo("acme");
	}

	@Test
	void threadLocalsAreCapturedWhenTheStreamIsSubscribed() {
		RecordingToolCallback toolCallback = new RecordingToolCallback(TOOL_NAME);

		ToolCallingAdvisor advisor = ToolCallingAdvisor.builder()
			.toolCallingManager(ToolCallingManager.builder().build())
			.build();

		ChatClientRequest request = createRequest(toolCallback);

		int[] callCount = { 0 };
		StreamAdvisor terminalAdvisor = new TerminalStreamAdvisor(
				(req, chain) -> Flux.just(callCount[0]++ == 0 ? responseWithToolCall() : finalResponse()));
		StreamAdvisorChain chain = streamChainOf(advisor, terminalAdvisor);

		// Assemble the stream while the thread holds one set of attributes. The advisor
		// is
		// called directly on purpose: entering through chain.nextStream() would defer
		// adviseStream() to subscription time and make the two moments indistinguishable,
		// hiding exactly what this test is about.
		RequestContextHolder.setRequestAttributes(requestAttributesWithTenant("acme"));
		Flux<ChatClientResponse> stream = advisor.adviseStream(request, chain);

		// The subscribing thread holds a different set of attributes, mimicking a
		// ThreadLocal that is replaced between assembly and subscription. The captured
		// state has to be the one of the subscribing thread, otherwise one request would
		// reuse the context of whichever request assembled the Flux.
		RequestContextHolder.setRequestAttributes(requestAttributesWithTenant("other"));

		List<ChatClientResponse> results = stream.collectList().block();

		assertThat(results).hasSize(1);
		assertThat(toolCallback.observedAttributes).isNotNull();
		assertThat(toolCallback.observedAttributes.getAttribute("tenant", RequestAttributes.SCOPE_REQUEST))
			.as("the attributes of the subscribing thread must be the ones captured")
			.isEqualTo("other");
	}

	private ChatClientRequest createRequest(ToolCallback... toolCallbacks) {
		ToolCallingChatOptions options = ToolCallingChatOptions.builder().toolCallbacks(List.of(toolCallbacks)).build();

		return ChatClientRequest.builder()
			.prompt(new Prompt(List.of(new UserMessage("test message")), options))
			.build();
	}

	private ChatClientResponse responseWithToolCall() {
		return responseWithToolCall("tool-call-1", TOOL_NAME);
	}

	private ChatClientResponse responseWithToolCall(String toolCallId, String toolName) {
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("response")
			.toolCalls(List.of(new AssistantMessage.ToolCall(toolCallId, "function", toolName, "{}")))
			.build();

		return ChatClientResponse.builder().chatResponse(chatResponse(assistantMessage)).build();
	}

	private ChatClientResponse finalResponse() {
		return ChatClientResponse.builder().chatResponse(chatResponse(new AssistantMessage("done"))).build();
	}

	private ChatResponse chatResponse(AssistantMessage message) {
		return ChatResponse.builder().generations(List.of(new Generation(message))).build();
	}

	private StreamAdvisorChain streamChainOf(Advisor... advisors) {
		return DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP).pushAll(List.of(advisors)).build();
	}

	private ServletRequestAttributes requestAttributesWithTenant(String tenant) {
		ServletRequestAttributes requestAttributes = new ServletRequestAttributes(new MockHttpServletRequest());
		requestAttributes.setAttribute("tenant", tenant, RequestAttributes.SCOPE_REQUEST);
		return requestAttributes;
	}

	/**
	 * Records the request context observed on the thread that actually executes the tool.
	 */
	private static final class RecordingToolCallback implements ToolCallback {

		private final String name;

		private volatile RequestAttributes observedAttributes;

		private volatile String threadName;

		private RecordingToolCallback(String name) {
			this.name = name;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return ToolDefinition.builder().name(this.name).description("test tool").inputSchema("{}").build();
		}

		@Override
		public String call(String toolInput) {
			this.threadName = Thread.currentThread().getName();
			this.observedAttributes = RequestContextHolder.getRequestAttributes();
			return "tool result";
		}

	}

	/**
	 * Minimal terminal {@link StreamAdvisor} that emits the responses produced by the
	 * supplied function.
	 */
	private static final class TerminalStreamAdvisor implements StreamAdvisor {

		private final java.util.function.BiFunction<ChatClientRequest, StreamAdvisorChain, Flux<ChatClientResponse>> responseFunction;

		private TerminalStreamAdvisor(
				java.util.function.BiFunction<ChatClientRequest, StreamAdvisorChain, Flux<ChatClientResponse>> responseFunction) {
			this.responseFunction = responseFunction;
		}

		@Override
		public String getName() {
			return "Terminal Stream Advisor";
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
				StreamAdvisorChain streamAdvisorChain) {
			return this.responseFunction.apply(chatClientRequest, streamAdvisorChain);
		}

	}

}
