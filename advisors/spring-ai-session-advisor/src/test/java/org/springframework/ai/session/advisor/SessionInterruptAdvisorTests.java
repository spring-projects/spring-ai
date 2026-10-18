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
import org.mockito.Mockito;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.status.StopReason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SessionInterruptAdvisor}: the status read before a round, on its own.
 *
 * @author Christian Tzolov
 */
class SessionInterruptAdvisorTests {

	private final SessionService sessionService = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private final SessionInterruptAdvisor advisor = SessionInterruptAdvisor.builder(this.sessionService).build();

	private final AdvisorChain chain = Mockito.mock(AdvisorChain.class);

	private final String sessionId = this.sessionService.create(CreateSessionRequest.builder().build()).id();

	@Test
	void passesTheRequestThroughWhileTheTurnRunsUnderTheRunId() {
		this.sessionService.turns().start(this.sessionId, "run-1");
		ChatClientRequest request = request("run-1");

		assertThat(this.advisor.before(request, this.chain)).isSameAs(request);
		ChatClientResponse response = ChatClientResponse.builder().build();
		assertThat(this.advisor.after(response, this.chain)).isSameAs(response);
	}

	@Test
	void throwsOnceTheTurnWasInterrupted() {
		this.sessionService.turns().start(this.sessionId, "run-1");
		ChatClientRequest request = request("run-1");
		assertThat(this.advisor.before(request, this.chain)).isSameAs(request);

		this.sessionService.turns().interrupt(this.sessionId, "stop");

		assertThatThrownBy(() -> this.advisor.before(request, this.chain)).isInstanceOf(TurnInterruptedException.class)
			.hasMessageContaining(this.sessionId)
			.hasMessageContaining("run-1")
			.hasMessageContaining("INTERRUPTED")
			.satisfies(ex -> {
				TurnInterruptedException interrupted = (TurnInterruptedException) ex;
				assertThat(interrupted.getSessionId()).isEqualTo(this.sessionId);
				assertThat(interrupted.getRunId()).isEqualTo("run-1");
				assertThat(interrupted.getStatus().stopReason()).isEqualTo(StopReason.INTERRUPTED);
			});
	}

	@Test
	void throwsWhenTheSessionRunsUnderAnotherRunOrNoneAtAll() {
		assertThatThrownBy(() -> this.advisor.before(request("run-1"), this.chain))
			.isInstanceOf(TurnInterruptedException.class);

		this.sessionService.turns().start(this.sessionId, "other");

		assertThatThrownBy(() -> this.advisor.before(request("run-1"), this.chain))
			.isInstanceOf(TurnInterruptedException.class)
			.hasMessageContaining("RUNNING");
	}

	@Test
	void requiresTheRunIdTheTurnAdvisorSets() {
		this.sessionService.turns().start(this.sessionId, "run-1");
		ChatClientRequest withoutRunId = ChatClientRequest.builder()
			.prompt(new Prompt("hi"))
			.context(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId)
			.build();

		assertThatIllegalStateException().isThrownBy(() -> this.advisor.before(withoutRunId, this.chain))
			.withMessageContaining("SessionTurnAdvisor");
	}

	@Test
	void defaultOrderIsJustInsideTheMemoryAdvisor() {
		assertThat(this.advisor.getOrder()).isEqualTo(SessionInterruptAdvisor.DEFAULT_ORDER)
			.isGreaterThan(ToolCallingAdvisor.DEFAULT_ORDER)
			.isGreaterThan(SessionMemoryAdvisor.builder(this.sessionService).build().getOrder());
		assertThat(SessionInterruptAdvisor.builder(this.sessionService).order(42).build().getOrder()).isEqualTo(42);
	}

	private ChatClientRequest request(String runId) {
		return ChatClientRequest.builder()
			.prompt(new Prompt("hi"))
			.context(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId)
			.context(SessionTurnAdvisor.RUN_ID_CONTEXT_KEY, runId)
			.build();
	}

}
