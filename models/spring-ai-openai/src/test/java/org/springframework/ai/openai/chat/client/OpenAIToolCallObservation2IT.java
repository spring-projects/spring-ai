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

package org.springframework.ai.openai.chat.client;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Hooks;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.DefaultChatClientBuilder;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationContext;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.test.chat.client.advisor.MockWeatherService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAIToolCallObservation2IT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@ActiveProfiles("logging-test")
class OpenAIToolCallObservation2IT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAIToolCallObservation2IT.class);

	/**
	 * The name returned by {@code ToolCallAdvisor.getName()}.
	 */
	static final String TOOL_CALLING_ADVISOR_NAME = "Tool Calling Advisor";

	static final String CHAT_MODEL_STREAM_ADVISOR_NAME = "stream";

	// =========================================================================
	// Shared assertion helpers (must appear before inner types per InnerTypeLast)
	// =========================================================================

	private static void assertChatClientIsRoot(ObservationCapture capture) {
		var chatClientNode = capture.findChatClient();
		assertThat(chatClientNode).as("ChatClientObservationContext must be present").isPresent();
		assertThat(chatClientNode.get().parentCtx()).as("spring.ai.chat.client must be the root span (no parent)")
			.isNull();
	}

	// =========================================================================
	// Per-test helper methods
	// =========================================================================

	private ChatClient buildChatClient(ObservationRegistry registry) {
		var model = OpenAiChatModel.builder()
			.observationRegistry(registry)
			.options(OpenAiChatOptions.builder()
				.apiKey(System.getenv("OPENAI_API_KEY"))
				.model(OpenAiChatOptions.DEFAULT_CHAT_MODEL)
				.build())
			.build();

		ToolCallAdvisor.Builder<?> toolCallAdvisorBuilder = ToolCallAdvisor.builder()
			.toolCallingManager(ToolCallingManager.builder().observationRegistry(registry).build());

		// Pass the same registry so ChatClientObservationContext,
		// AdvisorObservationContext,
		// and ChatModelObservationContext all share one registry and form a single tree.
		return new DefaultChatClientBuilder(model, registry, null, null, toolCallAdvisorBuilder).build();
	}

	private void logCapture(ObservationCapture capture) {
		logger.info("=== Observation timeline ({} events) ===", capture.fullTimeline().size());
		capture.fullTimeline().forEach(event -> {
			String kind = switch (event.kind()) {
				case START -> "START      ";
				case SCOPE_OPEN -> "SCOPE_OPEN ";
				case SCOPE_CLOSE -> "SCOPE_CLOSE";
			};
			String ctxLabel = ctxLabel(event.ctx());
			String parentLabel = event.parentCtx() != null ? " parent=" + ctxLabel(event.parentCtx()) : "";
			String thread = " [" + event.thread() + "]";
			logger.info("  {} {}{}{}", kind, ctxLabel, parentLabel, thread);
		});
	}

	private static String ctxLabel(Observation.Context ctx) {
		String type = ctx.getClass().getSimpleName();
		if (ctx instanceof AdvisorObservationContext a) {
			return type + "(" + a.getAdvisorName() + ")";
		}
		return type;
	}

	@Test
	void withToolCallsObservationTreeWithEnabledContextPropagation() {
		Hooks.enableAutomaticContextPropagation();
		withToolCallsObservationTree();
	}

	@Test
	void withToolCallsObservationTreeWithDisabledContextPropagation() {
		Hooks.disableAutomaticContextPropagation();
		withToolCallsObservationTree();
	}

	private void withToolCallsObservationTree() {

		// Hooks.disableAutomaticContextPropagation();

		var reg = TestObservationRegistry.create();
		var capture = new ObservationCapture();
		reg.observationConfig().observationHandler(capture.handler());

		buildChatClient(reg).prompt()
			.user("What is the weather in Tokyo?")
			.tools(new Tools())
			.stream()
			.content()
			.collectList()
			.block();

		logCapture(capture);

		assertChatClientIsRoot(capture);

		// context=ChatClientObservationContext parent=null
		assertThat(capture.nodes.get(0).ctx()).as("ChatClientObservationContext must be the root span")
			.isInstanceOf(ChatClientObservationContext.class);
		assertThat(capture.nodes.get(0).parentCtx())
			.as("ChatClientObservationContext must be the root span (no parent)")
			.isNull();

		// context=AdvisorObservationContext advisorName=Tool Calling Advisor
		// parent=ChatClientObservationContext
		assertThat(capture.nodes.get(1).ctx()).as("Second observation must be the Tool Calling Advisor span")
			.isInstanceOf(AdvisorObservationContext.class)
			.satisfies(ctx -> assertThat(((AdvisorObservationContext) ctx).getAdvisorName())
				.isEqualTo(TOOL_CALLING_ADVISOR_NAME));
		assertThat(capture.nodes.get(1).parentCtx())
			.as("Tool Calling Advisor parent must be ChatClientObservationContext")
			.isInstanceOf(ChatClientObservationContext.class);

		// context=AdvisorObservationContext advisorName=stream
		// parent=AdvisorObservationContext
		assertThat(capture.nodes.get(2).ctx()).as("Third observation must be the 'stream' advisor span")
			.isInstanceOf(AdvisorObservationContext.class)
			.satisfies(ctx -> assertThat(((AdvisorObservationContext) ctx).getAdvisorName())
				.isEqualTo(CHAT_MODEL_STREAM_ADVISOR_NAME));
		assertThat(capture.nodes.get(2).parentCtx()).as("'stream' advisor parent must be Tool Calling Advisor")
			.isInstanceOf(AdvisorObservationContext.class)
			.satisfies(ctx -> assertThat(((AdvisorObservationContext) ctx).getAdvisorName())
				.isEqualTo(TOOL_CALLING_ADVISOR_NAME));

		// context=ChatModelObservationContext parent=AdvisorObservationContext
		assertThat(capture.nodes.get(3).ctx()).as("Fourth observation must be the ChatModel span")
			.isInstanceOf(ChatModelObservationContext.class);
		assertThat(capture.nodes.get(3).parentCtx()).as("ChatModel span parent must be the 'stream' advisor")
			.isInstanceOf(AdvisorObservationContext.class)
			.satisfies(ctx -> assertThat(((AdvisorObservationContext) ctx).getAdvisorName())
				.isEqualTo(CHAT_MODEL_STREAM_ADVISOR_NAME));

		// context=ToolCallingObservationContext parent=AdvisorObservationContext
		assertThat(capture.nodes.get(4).ctx()).as("Fourth observation must be the ToolCalling span")
			.isInstanceOf(ToolCallingObservationContext.class);
		assertThat(capture.nodes.get(4).parentCtx()).as("ToolCalling span parent must be an AdvisorObservationContext")
			.isInstanceOf(AdvisorObservationContext.class)
			.satisfies(ctx -> assertThat(((AdvisorObservationContext) ctx).getAdvisorName())
				.isEqualTo(TOOL_CALLING_ADVISOR_NAME));

		// context=AdvisorObservationContext advisorName=stream
		// parent=AdvisorObservationContext
		assertThat(capture.nodes.get(5).ctx()).as("Fifth observation must be the second 'stream' advisor span")
			.isInstanceOf(AdvisorObservationContext.class)
			.satisfies(ctx -> assertThat(((AdvisorObservationContext) ctx).getAdvisorName())
				.isEqualTo(CHAT_MODEL_STREAM_ADVISOR_NAME));
		assertThat(capture.nodes.get(5).parentCtx()).as("Second 'stream' advisor parent must be Tool Calling Advisor")
			.isInstanceOf(AdvisorObservationContext.class)
			.satisfies(ctx -> assertThat(((AdvisorObservationContext) ctx).getAdvisorName())
				.isEqualTo(TOOL_CALLING_ADVISOR_NAME));

		// context=ChatModelObservationContext parent=AdvisorObservationContext
		assertThat(capture.nodes.get(6).ctx()).as("Sixth observation must be the second ChatModel span")
			.isInstanceOf(ChatModelObservationContext.class);
		assertThat(capture.nodes.get(6).parentCtx())
			.as("Second ChatModel span parent must be the second 'stream' advisor")
			.isInstanceOf(AdvisorObservationContext.class)
			.satisfies(ctx -> assertThat(((AdvisorObservationContext) ctx).getAdvisorName())
				.isEqualTo(CHAT_MODEL_STREAM_ADVISOR_NAME));

		var toolAdvisorNode = capture.findAdvisor(TOOL_CALLING_ADVISOR_NAME);
		assertThat(toolAdvisorNode).as("ToolCallAdvisor observation must be present in stream path").isPresent();
		assertThat(toolAdvisorNode.get().parentCtx())
			.as("Stream: ToolCallAdvisor parent must be ChatClientObservationContext")
			.isInstanceOf(ChatClientObservationContext.class);

		var chatModelNodes = capture.findAllByType(ChatModelObservationContext.class);
		assertThat(chatModelNodes).as("Expected at least 2 LLM calls in stream tool-calling path")
			.hasSizeGreaterThanOrEqualTo(2);
	}

	/**
	 * Captures the full observation lifecycle — starts, scope opens, and scope closes —
	 * in a single ordered timeline. Each event records the context, optional parent (for
	 * START events), and the thread name, making it straightforward to see which
	 * observations were active as thread-local current at each moment and on which
	 * thread.
	 *
	 * <p>
	 * The {@link #nodes} list is a filtered view of the timeline containing only START
	 * events; it preserves backward-compatible indexed access used by the existing tests.
	 */
	static final class ObservationCapture {

		final List<Node> nodes = new CopyOnWriteArrayList<>();

		private final List<TimelineEvent> timeline = new CopyOnWriteArrayList<>();

		ObservationHandler<Observation.Context> handler() {
			return new ObservationHandler<>() {
				@Override
				public void onStart(Observation.Context ctx) {
					Observation.Context parentCtx = null;
					var parentObs = ctx.getParentObservation();
					if (parentObs != null) {
						parentCtx = (Observation.Context) parentObs.getContextView();
					}
					nodes.add(new Node(ctx, parentCtx));
					timeline.add(new TimelineEvent(Kind.START, ctx, parentCtx, thread()));
				}

				@Override
				public void onScopeOpened(Observation.Context ctx) {
					timeline.add(new TimelineEvent(Kind.SCOPE_OPEN, ctx, null, thread()));
				}

				@Override
				public void onScopeClosed(Observation.Context ctx) {
					timeline.add(new TimelineEvent(Kind.SCOPE_CLOSE, ctx, null, thread()));
				}

				@Override
				public boolean supportsContext(Observation.Context ctx) {
					return true;
				}

				private String thread() {
					return Thread.currentThread().getName();
				}
			};
		}

		List<Node> all() {
			return Collections.unmodifiableList(this.nodes);
		}

		List<TimelineEvent> fullTimeline() {
			return Collections.unmodifiableList(this.timeline);
		}

		Optional<Node> findChatClient() {
			return this.nodes.stream().filter(n -> n.ctx() instanceof ChatClientObservationContext).findFirst();
		}

		Optional<Node> findAdvisor(String name) {
			return this.nodes.stream()
				.filter(n -> n.ctx() instanceof AdvisorObservationContext a && a.getAdvisorName().equals(name))
				.findFirst();
		}

		List<Node> findAllAdvisors(String name) {
			return this.nodes.stream()
				.filter(n -> n.ctx() instanceof AdvisorObservationContext a && a.getAdvisorName().equals(name))
				.toList();
		}

		List<Node> findAllByType(Class<? extends Observation.Context> type) {
			return this.nodes.stream().filter(n -> type.isInstance(n.ctx())).toList();
		}

		// Inner types declared last to satisfy the InnerTypeLast checkstyle rule.

		enum Kind {

			START, SCOPE_OPEN, SCOPE_CLOSE

		}

		record TimelineEvent(Kind kind, Observation.Context ctx, Observation.@Nullable Context parentCtx,
				String thread) {
		}

		record Node(Observation.Context ctx, Observation.@Nullable Context parentCtx) {
		}

	}

	@SpringBootConfiguration
	static class Config {

		// Intentionally empty: each test creates its own ObservationRegistry so that
		// handlers do not accumulate across methods. The Spring context is needed only
		// to satisfy @SpringBootTest and load the logging-test profile.

	}

	class Tools {

		private static final Logger logger = LoggerFactory.getLogger(Tools.class);

		private final MockWeatherService weatherService = new MockWeatherService();

		@Tool(description = "Get the current weather in a given location")
		public MockWeatherService.Response getCurrentWeather(MockWeatherService.Request request) {
			logger.info("Calling function to get weather by location");
			return this.weatherService.apply(request);
		}

	}

}
