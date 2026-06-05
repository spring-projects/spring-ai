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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that, when the {@link ToolCallingAdvisor} streaming loop runs, the tracing
 * <em>spans</em> reflect the observation hierarchy instead of collapsing onto whatever
 * span happens to be current on the calling thread.
 * <p>
 * The scenario reproduces a servlet-style request: an outer observation (think
 * {@code http.server.requests}) holds an open scope on the calling thread while the
 * reactive {@code ChatClient} pipeline is subscribed. The advisor observations carry
 * their parent through the Reactor context (not thread-local scopes), so without
 * intervention Micrometer's {@code TracingObservationHandler#getParentSpan} prefers the
 * still-open outer span over the explicit {@code parentObservation}, and the first
 * model-call span "escapes" to the outer span instead of nesting under the
 * {@code ToolCallingAdvisor} span.
 * <p>
 * This asserts span relationships ({@code parentId}/{@code spanId}) rather than logs. It
 * passes with the parent-scope handling in {@link DefaultAroundAdvisorChain#nextStream}
 * and fails without it (the first iteration's model span would be parented to the outer
 * span).
 *
 * @author Dariusz Jedrzejczyk
 */
class ToolCallingAdvisorSpanHierarchyTests {

	private static final String ADVISOR_NAME_TAG = "spring.ai.advisor.name";

	private static final String MODEL_ADVISOR_NAME = "Model Stream Advisor";

	@Test
	void modelCallSpansNestUnderToolCallingAdvisorEvenWhenAnOuterScopeIsOpen() {
		SimpleTracer tracer = new SimpleTracer();
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));

		// Mocked tool execution: one tool round, then the loop continues to the LLM
		// again.
		ToolCallingManager toolCallingManager = mock(ToolCallingManager.class);
		List<Message> conversationHistory = List.of(new UserMessage("What is the weather in Denver?"),
				AssistantMessage.builder().content("").build(), ToolResponseMessage.builder().build());
		when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
			.thenReturn(ToolExecutionResult.builder().conversationHistory(conversationHistory).build());

		ToolCallingAdvisor toolCallingAdvisor = ToolCallingAdvisor.builder()
			.toolCallingManager(toolCallingManager)
			.build();

		// Terminal advisor standing in for the model: first call returns a tool call,
		// second call (after tool execution) returns the final answer. Emits
		// synchronously
		// so the first iteration runs on the calling thread, under the outer scope.
		AtomicInteger calls = new AtomicInteger();
		StreamAdvisor modelAdvisor = new StreamAdvisor() {
			@Override
			public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
				ChatResponse response = calls.incrementAndGet() == 1 ? responseWithToolCall() : finalResponse();
				return Flux.just(ChatClientResponse.builder().chatResponse(response).build());
			}

			@Override
			public String getName() {
				return MODEL_ADVISOR_NAME;
			}

			@Override
			public int getOrder() {
				return Ordered.LOWEST_PRECEDENCE;
			}
		};

		StreamAdvisorChain chain = DefaultAroundAdvisorChain.builder(registry)
			.pushAll(List.<Advisor>of(toolCallingAdvisor, modelAdvisor))
			.build();

		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt(List.of(new UserMessage("What is the weather in Denver?")),
					ToolCallingChatOptions.builder().build()))
			.build();

		// Simulate the servlet HTTP span: an outer observation whose scope stays open on
		// the calling thread while the reactive pipeline is subscribed.
		Observation outer = Observation.createNotStarted("outer", registry).start();
		List<ChatClientResponse> results;
		try (Observation.Scope ignored = outer.openScope()) {
			results = chain.nextStream(request)
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, outer))
				.collectList()
				.block();
		}
		outer.stop();

		assertThat(results).isNotNull();
		assertThat(calls).hasValue(2); // one tool-call round + one final answer

		List<SimpleSpan> spans = new ArrayList<>(tracer.getSpans());

		SimpleSpan outerSpan = spanNamed(spans, "outer");
		SimpleSpan toolCallingAdvisorSpan = singleSpanForAdvisor(spans, toolCallingAdvisor.getName());
		List<SimpleSpan> modelSpans = spansForAdvisor(spans, MODEL_ADVISOR_NAME);

		// Sanity: the loop produced two model-call spans (the tool-requesting call and
		// the
		// follow-up call), and the ToolCallingAdvisor span sits under the outer span.
		assertThat(modelSpans).hasSize(2);
		assertThat(toolCallingAdvisorSpan.context().parentId())
			.as("the ToolCallingAdvisor span must be a child of the outer (HTTP-like) span")
			.isEqualTo(outerSpan.context().spanId());

		// The actual regression: every model-call span must nest under the
		// ToolCallingAdvisor
		// span. Without the parent-scope handling, the first iteration's span escapes to
		// the
		// outer span (because it is started on the calling thread while the outer scope
		// is
		// still open).
		assertThat(modelSpans).allSatisfy(span -> assertThat(span.context().parentId())
			.as("model-call span %s must nest under the ToolCallingAdvisor span (%s), not escape to the outer span (%s)",
					span.context().spanId(), toolCallingAdvisorSpan.context().spanId(), outerSpan.context().spanId())
			.isEqualTo(toolCallingAdvisorSpan.context().spanId()));
	}

	private static ChatResponse responseWithToolCall() {
		AssistantMessage assistantMessage = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "weather", "{}")))
			.build();
		return ChatResponse.builder().generations(List.of(new Generation(assistantMessage))).build();
	}

	private static ChatResponse finalResponse() {
		return ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("The weather in Denver is sunny."))))
			.build();
	}

	private static SimpleSpan spanNamed(List<SimpleSpan> spans, String name) {
		return spans.stream()
			.filter(span -> name.equals(span.getName()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No span named '" + name + "' in " + spanSummary(spans)));
	}

	private static List<SimpleSpan> spansForAdvisor(List<SimpleSpan> spans, String advisorName) {
		return spans.stream().filter(span -> advisorName.equals(span.getTags().get(ADVISOR_NAME_TAG))).toList();
	}

	private static SimpleSpan singleSpanForAdvisor(List<SimpleSpan> spans, String advisorName) {
		List<SimpleSpan> matches = spansForAdvisor(spans, advisorName);
		assertThat(matches).as("expected exactly one span for advisor '%s' in %s", advisorName, spanSummary(spans))
			.hasSize(1);
		return matches.get(0);
	}

	private static String spanSummary(List<SimpleSpan> spans) {
		return spans.stream()
			.map(span -> "%s(span=%s,parent=%s,advisor=%s)".formatted(span.getName(), span.context().spanId(),
					span.context().parentId(), span.getTags().get(ADVISOR_NAME_TAG)))
			.toList()
			.toString();
	}

}
