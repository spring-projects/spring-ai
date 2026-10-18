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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionRequest;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.ai.session.status.SessionStatus;
import org.springframework.ai.session.status.SessionStatusEvents;
import org.springframework.ai.session.status.StopReason;
import org.springframework.ai.session.status.TurnInProgressException;
import org.springframework.ai.session.status.TurnStatus;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SessionTurnAdvisor} around a real {@link ToolCallingAdvisor} loop and the
 * {@link SessionMemoryAdvisor}, driven through a {@link ChatClient} with a scripted
 * model: one turn per call, however the loop ends.
 *
 * @author Christian Tzolov
 */
class SessionTurnAdvisorTests {

	private final ScriptedChatModel model = new ScriptedChatModel();

	private final SessionService sessionService = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private final String sessionId = "s1";

	private final List<String> ran = new CopyOnWriteArrayList<>();

	@Test
	void callRecordsItsMessagesInLog() {
		this.model.thenAnswer("Hi!");

		say(client(), "Hello");

		assertThat(this.sessionService.getActiveMessages(this.sessionId)).extracting(m -> m.getText())
			.containsExactly("Hello", "Hi!");
	}

	@Test
	void returnDirectToolEndsCall() {
		this.model.thenCall("c1", "Lisbon").thenAnswer("Hello again.");
		ToolCallback direct = FunctionToolCallback.builder("get_weather", (City city) -> "Sunny in " + city.city())
			.description("The weather in a city")
			.inputType(City.class)
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.build();
		ChatClient client = client(direct, ToolCallingAdvisor.builder().build());

		assertThat(say(client, "Weather in Lisbon?")).contains("Sunny in Lisbon");

		// The next call starts its own turn
		assertThat(say(client, "Hi")).isEqualTo("Hello again.");
	}

	@Test
	void returnDirectResultsAreStoredAfterTheirCalls() {
		ChatClient client = looped(tool("get_weather", "Sunny in ", ToolMetadata.builder().returnDirect(true).build()),
				tool("get_time", "Noon in ", ToolMetadata.builder().returnDirect(true).build()));
		this.model.then(prompt -> calls(call("c1", "get_weather", "Paris"), call("c2", "get_time", "Paris")))
			.thenAnswer("You are welcome.");

		say(client, "Weather and time in Paris?");

		assertThat(toolResults(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("\"Sunny in Paris\"", "\"Noon in Paris\"");
		// The next turn's model sees what ran, and the history is valid as stored
		assertThat(say(client, "Thanks", "You are welcome.")).isEqualTo("You are welcome.");
		assertThat(ScriptedChatModel.describe(this.model.prompts().get(1)))
			.containsExactly("Weather and time in Paris?", "call:c1", "result:c1", "Thanks");
	}

	@Test
	void streamedReturnDirectResultIsStoredAfterItsCall() {
		ChatClient client = looped(tool("get_weather", "Sunny in ", ToolMetadata.builder().returnDirect(true).build()));
		this.model.thenCall("c1", "Paris");

		client.prompt()
			.user("Weather in Paris?")
			.advisors(SessionAdvisors.session(this.sessionId))
			.stream()
			.content()
			.blockLast();

		assertThat(ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("Weather in Paris?", "call:c1", "result:c1");
	}

	@Test
	void breachedLimitAnswersEveryCallOfBatch() {
		ToolCallingManager limited = ToolCallingManager.builder().maxTotalToolCalls(1).build();
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(SessionTurnAdvisor.builder(this.sessionService).build(),
					ToolCallingAdvisor.builder().toolCallingManager(limited).build(),
					SessionMemoryAdvisor.builder(this.sessionService).build())
			.defaultToolCallbacks(tool("get_weather", "Sunny in ", ToolMetadata.builder().build()))
			.build();
		this.model.then(prompt -> calls(call("c1", "get_weather", "Paris"), call("c2", "get_weather", "Rome"),
				call("c3", "get_weather", "Oslo")));

		say(client, "Weather in Paris, Rome and Oslo?");

		// The first call ran, the second broke the limit, the third never ran
		assertThat(this.ran).containsExactly("get_weather");
		List<String> results = toolResults(new Prompt(this.sessionService.getActiveMessages(this.sessionId)));
		assertThat(results).hasSize(3);
		assertThat(results.get(0)).isEqualTo("\"Sunny in Paris\"");
		assertThat(results.get(1)).isNotBlank().isNotEqualTo(SessionTurnAdvisor.NOT_RUN_TOOL_RESULT);
		assertThat(results.get(2)).isEqualTo(SessionTurnAdvisor.NOT_RUN_TOOL_RESULT);
	}

	@Test
	void breachedToolCallLimitEndsCall() {
		this.model.thenCall("c1", "Lisbon").thenCall("c2", "Porto").thenAnswer("Hello again.");
		ToolCallingAdvisor limited = ToolCallingAdvisor.builder()
			.toolCallingManager(ToolCallingManager.builder().maxTotalToolCalls(1).build())
			.build();
		ChatClient client = client(weather(), limited);

		say(client, "Weather in Lisbon and Porto?");

		assertThat(this.model.prompts()).hasSize(2);
		assertThat(say(client, "Hi")).isEqualTo("Hello again.");
	}

	@Test
	void streamedCallRecordsItsMessages() {
		this.model.thenAnswer("Hi!");

		List<ChatResponse> chunks = client().prompt()
			.user("Hello")
			.advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.stream()
			.chatResponse()
			.collectList()
			.block();

		assertThat(chunks).isNotEmpty();
		assertThat(this.sessionService.getActiveMessages(this.sessionId)).hasSize(2);
	}

	@Test
	void compactionRunsOnceTurnHasEnded() {
		AtomicInteger triggered = new AtomicInteger();
		SessionTurnAdvisor turn = SessionTurnAdvisor.builder(this.sessionService)
			.compactionTrigger(request -> triggered.incrementAndGet() > 0)
			.compactionStrategy(WindowCompactionStrategy.builder().lastEvents(2).build())
			.build();
		ChatClient client = client(weather(), turn, ToolCallingAdvisor.builder().build());
		say(client, "Hello", "Hi!");
		this.model.thenCall("c1", "Lisbon").thenAnswer("Sunny.");

		say(client, "Weather in Lisbon?");

		// Once per turn, not once per round of the tool loop
		assertThat(triggered).hasValue(2);
		// The window keeps the newest turn whole: the first one is compacted away
		assertThat(ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("Weather in Lisbon?", "call:c1", "result:c1", "Sunny.");
	}

	@Test
	void failedCompactionDoesNotFailCall() {
		SessionTurnAdvisor turn = SessionTurnAdvisor.builder(this.sessionService)
			.compactionTrigger(request -> true)
			.compactionStrategy(request -> {
				throw new IllegalStateException("summarizer unavailable");
			})
			.build();

		assertThat(say(client(weather(), turn, ToolCallingAdvisor.builder().build()), "Hello", "Hi!")).isEqualTo("Hi!");
		assertThat(this.sessionService.getActiveMessages(this.sessionId)).hasSize(2);
	}

	@Test
	void asyncCompactionRunsAfterCallReturns() throws Exception {
		HeldCompaction held = new HeldCompaction();
		SessionTurnAdvisor turn = SessionTurnAdvisor.builder(this.sessionService)
			.compactionTrigger(request -> true)
			.compactionStrategy(held)
			.asyncCompaction(true)
			.build();
		ChatClient client = client(weather(), turn, ToolCallingAdvisor.builder().build());

		// The reply comes back while the compaction is still held
		assertThat(say(client, "Hello", "Hi!")).isEqualTo("Hi!");
		assertThat(held.started.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(turn.isCompacting(this.sessionId)).isTrue();
		held.release.countDown();
		await(() -> !turn.isCompacting(this.sessionId));

		// The next turn's compaction keeps it, the newest turn
		say(client, "Still there?", "Yes.");
		await(() -> ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId)))
			.equals(List.of("Still there?", "Yes.")));
	}

	@Test
	void asyncCompactionThatNewTurnOvertakesKeepsThatTurnInView() throws Exception {
		this.sessionService.create(CreateSessionRequest.builder().id(this.sessionId).build());
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Earlier question"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("Earlier answer"));
		HeldCompaction held = new HeldCompaction();
		SessionTurnAdvisor turn = SessionTurnAdvisor.builder(this.sessionService)
			.compactionTrigger(request -> true)
			.compactionStrategy(held)
			.asyncCompaction(true)
			.build();
		ChatClient client = client(weather(), turn, ToolCallingAdvisor.builder().build());
		say(client, "Hello", "Hi!");
		assertThat(held.started.await(5, TimeUnit.SECONDS)).isTrue();

		// A second turn writes while the first compaction runs: no second compaction
		// starts, and the running one, overtaken, lands rebased onto the new turn, which
		// stays in the view verbatim
		say(client, "Still there?", "Yes.");
		held.release.countDown();
		await(() -> !turn.isCompacting(this.sessionId));
		assertThat(held.calls).hasValue(1);
		assertThat(ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("Hello", "Hi!", "Still there?", "Yes.");

		// The next turn's compaction lands
		say(client, "Bye", "Bye!");
		await(() -> ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId)))
			.equals(List.of("Bye", "Bye!")));
		assertThat(held.calls).hasValue(2);
	}

	@Test
	void asyncCompactionRunsWithCallersContext() throws Exception {
		// Stands in for Spring Security's SecurityContext, which registers the same way
		ThreadLocal<String> caller = new ThreadLocal<>();
		ContextRegistry.getInstance()
			.registerThreadLocalAccessor("test.caller", caller::get, caller::set, caller::remove);
		AtomicReference<String> seen = new AtomicReference<>();
		CountDownLatch compacted = new CountDownLatch(1);
		CompactionStrategy recording = request -> {
			seen.set(caller.get());
			compacted.countDown();
			return CompactionResult.unchanged();
		};
		ChatClient client = client(weather(),
				SessionTurnAdvisor.builder(this.sessionService)
					.compactionTrigger(request -> true)
					.compactionStrategy(recording)
					.asyncCompaction(true)
					.build(),
				ToolCallingAdvisor.builder().build());
		try {
			caller.set("alice");
			say(client, "Hello", "Hi!");

			assertThat(compacted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(seen).hasValue("alice");
		}
		finally {
			caller.remove();
			ContextRegistry.getInstance().removeThreadLocalAccessor("test.caller");
		}
	}

	@Test
	void compactionOptionsAreSetTogether() {
		assertThatThrownBy(() -> SessionTurnAdvisor.builder(this.sessionService)
			.compactionStrategy(WindowCompactionStrategy.builder().build())
			.build()).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void chatClientDefaultMetadataLabelsSessionsTurnAdvisorCreates() {
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(SessionTurnAdvisor.builder(this.sessionService).build(),
					SessionMemoryAdvisor.builder(this.sessionService).build())
			.defaultAdvisors(a -> a.param(SessionMemoryAdvisor.SESSION_METADATA_CONTEXT_KEY,
					Map.of(SessionAttributes.AGENT_NAME, "support", SessionAttributes.AGENT_VERSION, "2")))
			.build();

		say(client, "Hello", "Hi!");

		assertThat(SessionAttributes.agentName(session())).isEqualTo("support");
		assertThat(SessionAttributes.agentVersion(session())).isEqualTo("2");
	}

	// --- Turn status ---

	@Test
	void callStartsAndEndsTheTurn() {
		say(client(), "Hello", "Hi!");

		TurnStatus status = this.sessionService.turns().status(this.sessionId);
		assertThat(status.status()).isEqualTo(SessionStatus.IDLE);
		assertThat(status.stopReason()).isEqualTo(StopReason.END_TURN);
		List<SessionEvent> statuses = statusEvents();
		assertThat(statuses).hasSize(2);
		assertThat(SessionStatusEvents.status(statuses.get(0))).isEqualTo(SessionStatus.RUNNING);
		assertThat(SessionStatusEvents.status(statuses.get(1))).isEqualTo(SessionStatus.IDLE);
		assertThat(SessionStatusEvents.runId(statuses.get(0))).isNotBlank()
			.isEqualTo(SessionStatusEvents.runId(statuses.get(1)))
			.isEqualTo(status.runId());
		// The turn brackets the conversation in the log, and stays out of the prompt
		assertThat(this.sessionService.getEvents(this.sessionId)).extracting(SessionEvent::getType)
			.containsExactly(EventType.STATUS, EventType.MESSAGE, EventType.MESSAGE, EventType.STATUS);
		assertThat(this.sessionService.getActiveMessages(this.sessionId)).hasSize(2);
	}

	@Test
	void turnIsStartedOncePerCallNotPerRound() {
		this.model.thenCall("c1", "Lisbon").thenAnswer("Sunny.");

		say(client(), "Weather in Lisbon?");

		assertThat(this.model.prompts()).hasSize(2);
		assertThat(statusEvents()).hasSize(2);
	}

	@Test
	void failingCallEndsTheTurnWithError() {
		this.model.then(prompt -> {
			throw new IllegalStateException("model down");
		});

		assertThatThrownBy(() -> say(client(), "Hello")).isInstanceOf(IllegalStateException.class)
			.hasMessage("model down");

		TurnStatus status = this.sessionService.turns().status(this.sessionId);
		assertThat(status.status()).isEqualTo(SessionStatus.IDLE);
		assertThat(status.stopReason()).isEqualTo(StopReason.ERROR);
		// The next call runs
		assertThat(say(client(), "Hello again", "Hi!")).isEqualTo("Hi!");
		assertThat(statusEvents()).hasSize(4);
	}

	@Test
	void streamedCallStartsAndEndsTheTurn() {
		this.model.thenAnswer("Hi!");

		client().prompt()
			.user("Hello")
			.advisors(SessionAdvisors.session(this.sessionId))
			.stream()
			.content()
			.blockLast();

		assertThat(this.sessionService.turns().status(this.sessionId).stopReason()).isEqualTo(StopReason.END_TURN);
		assertThat(statusEvents()).hasSize(2);
	}

	@Test
	void failingStreamEndsTheTurnWithError() {
		this.model.then(prompt -> {
			throw new IllegalStateException("model down");
		});

		assertThatThrownBy(() -> client().prompt()
			.user("Hello")
			.advisors(SessionAdvisors.session(this.sessionId))
			.stream()
			.content()
			.blockLast()).isInstanceOf(IllegalStateException.class).hasMessage("model down");

		assertThat(this.sessionService.turns().status(this.sessionId).stopReason()).isEqualTo(StopReason.ERROR);
		assertThat(statusEvents()).hasSize(2);
	}

	@Test
	void cancelledStreamEndsTheTurnWithCancelled() throws InterruptedException {
		ScriptedChatModel endless = new ScriptedChatModel() {
			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.never();
			}
		};
		ChatClient client = ChatClient.builder(endless)
			.defaultAdvisors(SessionTurnAdvisor.builder(this.sessionService).build(),
					SessionMemoryAdvisor.builder(this.sessionService).build())
			.build();

		Disposable subscription = client.prompt()
			.user("Hello")
			.advisors(SessionAdvisors.session(this.sessionId))
			.stream()
			.content()
			.subscribe();
		await(() -> this.sessionService.turns().status(this.sessionId).isRunning());
		subscription.dispose();

		await(() -> this.sessionService.turns().status(this.sessionId).stopReason() == StopReason.CANCELLED);
		// No INTERRUPT event: nobody asked the session to stop
		assertThat(statusEvents()).hasSize(2);
		assertThat(interruptEvents()).isEmpty();
	}

	@Test
	void cancelledStreamEndsTheTurnOnTheCancellingThreadWhenTheSchedulerIsGone() throws InterruptedException {
		ScriptedChatModel endless = new ScriptedChatModel() {
			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.never();
			}
		};
		Scheduler scheduler = Schedulers.newSingle("turn-status");
		ChatClient client = ChatClient.builder(endless)
			.defaultAdvisors(SessionTurnAdvisor.builder(this.sessionService).scheduler(scheduler).build(),
					SessionMemoryAdvisor.builder(this.sessionService).build())
			.build();

		Disposable subscription = client.prompt()
			.user("Hello")
			.advisors(SessionAdvisors.session(this.sessionId))
			.stream()
			.content()
			.subscribe();
		await(() -> this.sessionService.turns().status(this.sessionId).isRunning());
		// The scheduler goes away while the stream is open, as at shutdown
		scheduler.dispose();
		subscription.dispose();

		// Ended on the cancelling thread, since the scheduler took no more work
		assertThat(this.sessionService.turns().status(this.sessionId).stopReason()).isEqualTo(StopReason.CANCELLED);
		assertThat(statusEvents()).hasSize(2);
	}

	@Test
	void runIdFromTheContextIsUsedAndForwarded() {
		this.model.thenAnswer("Hi!");

		ChatClientResponse response = client().prompt()
			.user("Hello")
			.advisors(SessionAdvisors.session(this.sessionId, "run-42"))
			.call()
			.chatClientResponse();

		assertThat(response.context()).containsEntry(SessionTurnAdvisor.RUN_ID_CONTEXT_KEY, "run-42");
		assertThat(statusEvents()).hasSize(2)
			.allSatisfy(event -> assertThat(SessionStatusEvents.runId(event)).isEqualTo("run-42"));
		assertThat(this.sessionService.turns().status(this.sessionId).runId()).isEqualTo("run-42");
	}

	@Test
	void generatedRunIdIsForwarded() {
		this.model.thenAnswer("Hi!");

		ChatClientResponse response = client().prompt()
			.user("Hello")
			.advisors(SessionAdvisors.session(this.sessionId))
			.call()
			.chatClientResponse();

		Object runId = response.context().get(SessionTurnAdvisor.RUN_ID_CONTEXT_KEY);
		assertThat(runId).isInstanceOf(String.class);
		assertThat(this.sessionService.turns().status(this.sessionId).runId()).isEqualTo(runId);
	}

	@Test
	void callTakesTheDefaultLeaseOnTheTurn() {
		Instant before = Instant.now();
		say(client(), "Hello", "Hi!");

		TurnStatus started = TurnStatus.from(statusEvents().get(0));
		assertThat(started.expiresAt()).isNotNull()
			.isBetween(before.plus(SessionTurnAdvisor.DEFAULT_TURN_TIMEOUT),
					Instant.now().plus(SessionTurnAdvisor.DEFAULT_TURN_TIMEOUT));
	}

	@Test
	void turnTimeoutNullTakesNoLease() {
		ChatClient client = client(weather(), SessionTurnAdvisor.builder(this.sessionService).turnTimeout(null).build(),
				ToolCallingAdvisor.builder().build());
		say(client, "Hello", "Hi!");

		assertThat(TurnStatus.from(statusEvents().get(0)).expiresAt()).isNull();
	}

	@Test
	void callTakesOverATurnWhoseLeaseRanOut() {
		// A call on another JVM died an hour ago between the start and the end of its
		// turn
		this.sessionService.create(CreateSessionRequest.builder().id(this.sessionId).build());
		this.sessionService
			.appendEvent(SessionStatusEvents.running(this.sessionId, "dead", Instant.now().minus(Duration.ofHours(1))));

		assertThat(say(client(), "Hello", "Hi!")).isEqualTo("Hi!");

		List<SessionEvent> statuses = statusEvents();
		assertThat(statuses).hasSize(4);
		assertThat(TurnStatus.from(statuses.get(1)).stopReason()).isEqualTo(StopReason.EXPIRED);
		assertThat(SessionStatusEvents.runId(statuses.get(1))).isEqualTo("dead");
		assertThat(this.sessionService.turns().status(this.sessionId).stopReason()).isEqualTo(StopReason.END_TURN);
		assertThat(interruptEvents()).isEmpty();
	}

	@Test
	void busySessionFailsTheCall() {
		this.sessionService.create(CreateSessionRequest.builder().id(this.sessionId).build());
		this.sessionService.turns().start(this.sessionId, "other");
		this.model.thenAnswer("Hi!");

		assertThatThrownBy(() -> say(client(), "Hello")).isInstanceOf(TurnInProgressException.class)
			.satisfies(ex -> assertThat(((TurnInProgressException) ex).getRunId()).isEqualTo("other"));

		// Nothing ran, nothing was stored, the other turn still runs
		assertThat(this.model.prompts()).isEmpty();
		assertThat(this.sessionService.getActiveMessages(this.sessionId)).isEmpty();
		assertThat(this.sessionService.turns().status(this.sessionId).isRunning("other")).isTrue();
	}

	@Test
	void interruptDuringTheTurnStandsWhenTheCallCompletes() {
		// Without the interrupt advisor the loop runs on; the interrupt still wins the
		// status, the advisor's own end of the turn is a no-op
		ChatClient client = client(stopTool(), ToolCallingAdvisor.builder().build());
		this.model.then(prompt -> calls(call("c1", "stop", "Lisbon"))).thenAnswer("Stopped.");

		assertThat(say(client, "Stop")).isEqualTo("Stopped.");

		TurnStatus status = this.sessionService.turns().status(this.sessionId);
		assertThat(status.stopReason()).isEqualTo(StopReason.INTERRUPTED);
		assertThat(statusEvents()).hasSize(2);
		assertThat(this.sessionService.getEvents(this.sessionId))
			.filteredOn(event -> EventType.INTERRUPT.equals(event.getType()))
			.hasSize(1);
		assertThat(this.model.prompts()).hasSize(2);
	}

	@Test
	void interruptibleLoopStopsBeforeTheNextRound() {
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(SessionAdvisors.builder(this.sessionService).interruptible(true).build())
			.defaultToolCallbacks(stopTool())
			.build();
		this.model.then(prompt -> calls(call("c1", "stop", "Lisbon"))).thenAnswer("Stopped.");

		assertThatThrownBy(() -> say(client, "Stop")).isInstanceOf(TurnInterruptedException.class).satisfies(ex -> {
			TurnInterruptedException interrupted = (TurnInterruptedException) ex;
			assertThat(interrupted.getSessionId()).isEqualTo(this.sessionId);
			assertThat(interrupted.getRunId()).isEqualTo(this.sessionService.turns().status(this.sessionId).runId());
			assertThat(interrupted.getStatus().stopReason()).isEqualTo(StopReason.INTERRUPTED);
		});

		// One model round: the second never ran, and the status the interrupt set stands
		assertThat(this.model.prompts()).hasSize(1);
		assertThat(this.sessionService.turns().status(this.sessionId).stopReason()).isEqualTo(StopReason.INTERRUPTED);
		assertThat(statusEvents()).hasSize(2);
		// The round that ran is whole in the log, the result of the stop tool included
		assertThat(ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("Stop", "call:c1", "result:c1");
	}

	@Test
	void interruptibleStreamStopsBeforeTheNextRound() {
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(SessionAdvisors.builder(this.sessionService).interruptible(true).build())
			.defaultToolCallbacks(stopTool())
			.build();
		this.model.then(prompt -> calls(call("c1", "stop", "Lisbon"))).thenAnswer("Stopped.");

		assertThatThrownBy(() -> client.prompt()
			.user("Stop")
			.advisors(SessionAdvisors.session(this.sessionId))
			.stream()
			.content()
			.blockLast()).isInstanceOf(TurnInterruptedException.class);

		assertThat(this.model.prompts()).hasSize(1);
		assertThat(this.sessionService.turns().status(this.sessionId).stopReason()).isEqualTo(StopReason.INTERRUPTED);
		assertThat(statusEvents()).hasSize(2);
		assertThat(ScriptedChatModel.describe(new Prompt(this.sessionService.getActiveMessages(this.sessionId))))
			.containsExactly("Stop", "call:c1", "result:c1");
	}

	/** A tool that interrupts the session, standing in for another thread. */
	private ToolCallback stopTool() {
		return FunctionToolCallback.builder("stop", (City city) -> {
			this.sessionService.turns().interrupt(this.sessionId, "user pressed stop");
			return "stopping in " + city.city();
		}).description("Stops the turn").inputType(City.class).build();
	}

	private List<SessionEvent> statusEvents() {
		return this.sessionService.getEvents(this.sessionId)
			.stream()
			.filter(event -> EventType.STATUS.equals(event.getType()))
			.toList();
	}

	private List<SessionEvent> interruptEvents() {
		return this.sessionService.getEvents(this.sessionId)
			.stream()
			.filter(event -> EventType.INTERRUPT.equals(event.getType()))
			.toList();
	}

	// --- Tool calls that need confirmation ---

	private ChatClient client() {
		return client(weather(), ToolCallingAdvisor.builder().build());
	}

	private ChatClient client(ToolCallback tool, ToolCallingAdvisor toolCalling) {
		return client(tool, SessionTurnAdvisor.builder(this.sessionService).build(), toolCalling);
	}

	private ChatClient client(ToolCallback tool, Advisor... advisors) {
		return ChatClient.builder(this.model)
			.defaultAdvisors(advisors)
			.defaultAdvisors(SessionMemoryAdvisor.builder(this.sessionService).build())
			.defaultToolCallbacks(tool)
			.build();
	}

	@Test
	void streamThatFailsBeforeItStartsEndsTheTurnWithError() {
		// A downstream advisor fails while the stream is assembled, before any operator
		// of the turn advisor could see an error
		StreamAdvisor broken = new StreamAdvisor() {

			@Override
			public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
				throw new IllegalStateException("broken advisor");
			}

			@Override
			public String getName() {
				return "broken";
			}

			@Override
			public int getOrder() {
				return SessionTurnAdvisor.DEFAULT_ORDER + 50;
			}

		};
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(SessionTurnAdvisor.builder(this.sessionService).build(), broken,
					SessionMemoryAdvisor.builder(this.sessionService).build())
			.build();

		assertThatThrownBy(() -> client.prompt()
			.user("Hello")
			.advisors(SessionAdvisors.session(this.sessionId))
			.stream()
			.content()
			.blockLast()).satisfiesAnyOf(ex -> assertThat(ex).hasMessageContaining("broken advisor"),
					ex -> assertThat(ex).hasRootCauseMessage("broken advisor"));

		TurnStatus status = this.sessionService.turns().status(this.sessionId);
		assertThat(status.isRunning()).isFalse();
		assertThat(status.stopReason()).isEqualTo(StopReason.ERROR);
	}

	/**
	 * The turn advisor around an explicit tool-calling loop, with the memory advisor
	 * inside.
	 */
	private ChatClient looped(ToolCallback... tools) {
		return ChatClient.builder(this.model)
			.defaultAdvisors(SessionTurnAdvisor.builder(this.sessionService).build(),
					ToolCallingAdvisor.builder().build(), SessionMemoryAdvisor.builder(this.sessionService).build())
			.defaultToolCallbacks(tools)
			.build();
	}

	private String say(ChatClient client, String text) {
		return client.prompt()
			.user(text)
			.advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.call()
			.content();
	}

	private String say(ChatClient client, String text, String reply) {
		this.model.thenAnswer(reply);
		return say(client, text);
	}

	private ToolCallback tool(String name, String result, ToolMetadata metadata) {
		return FunctionToolCallback.builder(name, (City city) -> {
			this.ran.add(name);
			return result + city.city();
		}).description("A tool about a city").inputType(City.class).toolMetadata(metadata).build();
	}

	private static AssistantMessage.ToolCall call(String id, String tool, String city) {
		return new AssistantMessage.ToolCall(id, "function", tool, "{\"city\":\"" + city + "\"}");
	}

	private static ChatResponse calls(AssistantMessage.ToolCall... calls) {
		return new ChatResponse(
				List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(calls)).build())));
	}

	private static List<String> toolResults(Prompt prompt) {
		return prompt.getInstructions()
			.stream()
			.filter(ToolResponseMessage.class::isInstance)
			.map(ToolResponseMessage.class::cast)
			.flatMap(results -> results.getResponses().stream())
			.map(ToolResponseMessage.ToolResponse::responseData)
			.toList();
	}

	private static ToolCallback weather() {
		return FunctionToolCallback.builder("get_weather", (City city) -> "Sunny in " + city.city())
			.description("The weather in a city")
			.inputType(City.class)
			.build();
	}

	private static ChatResponse answer(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	private Session session() {
		return this.sessionService.findById(this.sessionId);
	}

	private static void await(BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) {
			Thread.sleep(50);
		}
		assertThat(condition.getAsBoolean()).isTrue();
	}

	@Test
	void streamedCallCarriesCallersContextToSessionCalls() throws InterruptedException {
		ThreadLocal<String> caller = new ThreadLocal<>();
		ContextRegistry.getInstance()
			.registerThreadLocalAccessor("test.caller", caller::get, caller::set, caller::remove);
		AtomicReference<String> seenByCompaction = new AtomicReference<>();
		AtomicReference<String> seenByMemory = new AtomicReference<>();
		CountDownLatch compacted = new CountDownLatch(1);
		CompactionStrategy recording = request -> {
			seenByCompaction.set(caller.get());
			compacted.countDown();
			return CompactionResult.unchanged();
		};
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(
					SessionTurnAdvisor.builder(this.sessionService)
						.compactionTrigger(request -> true)
						.compactionStrategy(recording)
						.build(),
					ToolCallingAdvisor.builder().build(),
					SessionMemoryAdvisor.builder(this.sessionService).messageFilter(message -> {
						seenByMemory.set(caller.get());
						return true;
					}).build())
			.build();
		this.model.thenAnswer("Hi!");
		try {
			caller.set("alice");
			client.prompt()
				.user("Hello")
				.advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
				.stream()
				.content()
				.blockLast();

			assertThat(compacted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(seenByMemory).hasValue("alice");
			assertThat(seenByCompaction).hasValue("alice");
		}
		finally {
			caller.remove();
			ContextRegistry.getInstance().removeThreadLocalAccessor("test.caller");
		}
	}

	@Test
	void streamedCallCarriesReactorContextToSessionCalls() throws InterruptedException {
		// A reactive caller, under WebFlux for example, has no thread locals: its
		// security context travels in the Reactor context
		ThreadLocal<String> caller = new ThreadLocal<>();
		ContextRegistry.getInstance()
			.registerThreadLocalAccessor("test.caller", caller::get, caller::set, caller::remove);
		AtomicReference<String> seenByCompaction = new AtomicReference<>();
		AtomicReference<String> seenByMemory = new AtomicReference<>();
		CountDownLatch compacted = new CountDownLatch(1);
		CompactionStrategy recording = request -> {
			seenByCompaction.set(caller.get());
			compacted.countDown();
			return CompactionResult.unchanged();
		};
		ChatClient client = ChatClient.builder(this.model)
			.defaultAdvisors(
					SessionTurnAdvisor.builder(this.sessionService)
						.compactionTrigger(request -> true)
						.compactionStrategy(recording)
						.build(),
					ToolCallingAdvisor.builder().build(),
					SessionMemoryAdvisor.builder(this.sessionService).messageFilter(message -> {
						seenByMemory.set(caller.get());
						return true;
					}).build())
			.build();
		this.model.thenAnswer("Hi!");
		try {
			client.prompt()
				.user("Hello")
				.advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
				.stream()
				.content()
				.contextWrite(context -> context.put("test.caller", "bob"))
				.blockLast();

			assertThat(compacted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(seenByMemory).hasValue("bob");
			assertThat(seenByCompaction).hasValue("bob");
			assertThat(caller.get()).isNull();
		}
		finally {
			caller.remove();
			ContextRegistry.getInstance().removeThreadLocalAccessor("test.caller");
		}
	}

	record City(String city) {
	}

	/** Keeps the newest turn, once released. */
	static final class HeldCompaction implements CompactionStrategy {

		final CountDownLatch started = new CountDownLatch(1);

		final CountDownLatch release = new CountDownLatch(1);

		final AtomicInteger calls = new AtomicInteger();

		@Override
		public CompactionResult compact(CompactionRequest request) {
			this.calls.incrementAndGet();
			this.started.countDown();
			try {
				this.release.await(5, TimeUnit.SECONDS);
				return WindowCompactionStrategy.builder().lastEvents(2).build().compact(request);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
		}

	}

}
