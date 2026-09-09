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

package org.springframework.ai.chat.client.advisor.governance;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link GovernanceAdvisor}.
 *
 * @author Spring AI Contributors
 */
@ExtendWith(MockitoExtension.class)
class GovernanceAdvisorTests {

	@Mock
	private ChatModel chatModel;

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static ChatResponse okResponse() {
		return ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage("ok")))).build();
	}

	private ChatClient chatClientWith(GovernanceAdvisor advisor) {
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());
		return ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();
	}

	// -------------------------------------------------------------------------
	// ENFORCE — PII detection
	// -------------------------------------------------------------------------

	@Test
	void blocksPiiInEnforceMode() {
		GovernanceAdvisor advisor = GovernanceAdvisor.builder().mode(GovernanceMode.ENFORCE).build();
		ChatClient client = chatClientWith(advisor);

		assertThatThrownBy(() -> client.prompt().user("My SSN is 123-45-6789").call().chatResponse())
			.isInstanceOf(GovernanceViolationException.class)
			.satisfies(ex -> {
				GovernanceDecision d = ((GovernanceViolationException) ex).getDecision();
				assertThat(d.action()).isEqualTo(GovernanceDecision.ACTION_DENY);
				assertThat(d.reason()).containsIgnoringCase("PII");
				assertThat(d.riskScore()).isGreaterThan(0.0);
			});

		verify(this.chatModel, never()).call(any(Prompt.class));
	}

	@Test
	void allowsCleanPromptInEnforceMode() {
		given(this.chatModel.call(any(Prompt.class))).willReturn(okResponse());

		GovernanceAdvisor advisor = GovernanceAdvisor.builder().mode(GovernanceMode.ENFORCE).build();
		ChatClient client = chatClientWith(advisor);

		ChatResponse response = client.prompt()
			.user("Explain Spring Boot security best practices")
			.call()
			.chatResponse();

		assertThat(response).isNotNull();
	}

	// -------------------------------------------------------------------------
	// OBSERVE — PII passes through
	// -------------------------------------------------------------------------

	@Test
	void piiPassesThroughInObserveMode() {
		given(this.chatModel.call(any(Prompt.class))).willReturn(okResponse());

		GovernanceAdvisor advisor = GovernanceAdvisor.builder().mode(GovernanceMode.OBSERVE).build();
		ChatClient client = chatClientWith(advisor);

		// Should NOT throw in OBSERVE mode
		ChatResponse response = client.prompt().user("My SSN is 123-45-6789").call().chatResponse();

		assertThat(response).isNotNull();
		verify(this.chatModel).call(any(Prompt.class));
	}

	// -------------------------------------------------------------------------
	// MONITOR — PII passes through
	// -------------------------------------------------------------------------

	@Test
	void piiPassesThroughInMonitorMode() {
		given(this.chatModel.call(any(Prompt.class))).willReturn(okResponse());

		List<GovernanceDecision> audit = new ArrayList<>();
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
			.mode(GovernanceMode.MONITOR)
			.auditListener(audit::add)
			.build();
		ChatClient client = chatClientWith(advisor);

		client.prompt().user("My SSN is 123-45-6789").call().chatResponse();

		assertThat(audit).hasSize(1);
		assertThat(audit.get(0).action()).isEqualTo(GovernanceDecision.ACTION_DENY);
	}

	// -------------------------------------------------------------------------
	// Budget enforcement
	// -------------------------------------------------------------------------

	@Test
	void blocksBudgetExceededInEnforceMode() {
		SessionCostTracker tracker = new SessionCostTracker(10L);
		// Pre-seed the session to already be at the limit.
		tracker.recordUsage("session-1", buildResponse(10L));

		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
			.mode(GovernanceMode.ENFORCE)
			.costTracker(tracker)
			.piiEnabled(false)
			.build();
		ChatClient client = chatClientWith(advisor);

		assertThatThrownBy(() -> client.prompt()
			.user("What is 1 + 1?")
			.advisors(a -> a.param(GovernanceAdvisor.DEFAULT_SESSION_ID_CONTEXT_KEY, "session-1"))
			.call()
			.chatResponse()).isInstanceOf(GovernanceViolationException.class).satisfies(ex -> {
				GovernanceDecision d = ((GovernanceViolationException) ex).getDecision();
				assertThat(d.action()).isEqualTo(GovernanceDecision.ACTION_DENY);
				assertThat(d.reason()).containsIgnoringCase("budget");
			});

		verify(this.chatModel, never()).call(any(Prompt.class));
	}

	@Test
	void allowsRequestWithinBudget() {
		given(this.chatModel.call(any(Prompt.class))).willReturn(okResponse());

		SessionCostTracker tracker = new SessionCostTracker(1000L);
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
			.mode(GovernanceMode.ENFORCE)
			.costTracker(tracker)
			.piiEnabled(false)
			.build();
		ChatClient client = chatClientWith(advisor);

		ChatResponse response = client.prompt()
			.user("What is 1 + 1?")
			.advisors(a -> a.param(GovernanceAdvisor.DEFAULT_SESSION_ID_CONTEXT_KEY, "session-2"))
			.call()
			.chatResponse();

		assertThat(response).isNotNull();
	}

	// -------------------------------------------------------------------------
	// Audit listener
	// -------------------------------------------------------------------------

	@Test
	void auditListenerReceivesAllowDecision() {
		given(this.chatModel.call(any(Prompt.class))).willReturn(okResponse());

		List<GovernanceDecision> decisions = new ArrayList<>();
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
			.mode(GovernanceMode.ENFORCE)
			.auditListener(decisions::add)
			.build();
		ChatClient client = chatClientWith(advisor);

		client.prompt().user("Explain Spring Boot").call().chatResponse();

		assertThat(decisions).hasSize(1);
		assertThat(decisions.get(0).isAllowed()).isTrue();
		assertThat(decisions.get(0).correlationId()).isNotBlank();
		assertThat(decisions.get(0).evaluationTimeMs()).isGreaterThanOrEqualTo(0L);
	}

	@Test
	void auditListenerReceivesDenyDecision() {
		List<GovernanceDecision> decisions = new ArrayList<>();
		GovernanceAdvisor advisor = GovernanceAdvisor.builder()
			.mode(GovernanceMode.ENFORCE)
			.auditListener(decisions::add)
			.build();
		ChatClient client = chatClientWith(advisor);

		assertThatThrownBy(() -> client.prompt().user("My SSN is 123-45-6789").call().chatResponse());

		assertThat(decisions).hasSize(1);
		assertThat(decisions.get(0).isDenied()).isTrue();
		assertThat(decisions.get(0).riskScore()).isGreaterThan(0.0);
	}

	// -------------------------------------------------------------------------
	// Governance decision in response context
	// -------------------------------------------------------------------------

	@Test
	void governanceDecisionStoredInResponseContext() {
		given(this.chatModel.call(any(Prompt.class))).willReturn(okResponse());

		GovernanceAdvisor advisor = GovernanceAdvisor.builder().mode(GovernanceMode.ENFORCE).build();
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		ChatClient client = ChatClient.builder(this.chatModel).defaultAdvisors(advisor).build();

		// Use the lower-level API to inspect the context map.
		var response = client.prompt().user("Hello").call().chatClientResponse();
		assertThat(response).isNotNull();
		assertThat(response.context()).containsKey(GovernanceAdvisor.GOVERNANCE_DECISION_CONTEXT_KEY);

		GovernanceDecision d = (GovernanceDecision) response.context()
			.get(GovernanceAdvisor.GOVERNANCE_DECISION_CONTEXT_KEY);
		assertThat(d).isNotNull();
		assertThat(d.isAllowed()).isTrue();
	}

	// -------------------------------------------------------------------------
	// PII disabled
	// -------------------------------------------------------------------------

	@Test
	void piiCheckCanBeDisabled() {
		given(this.chatModel.call(any(Prompt.class))).willReturn(okResponse());

		GovernanceAdvisor advisor = GovernanceAdvisor.builder().mode(GovernanceMode.ENFORCE).piiEnabled(false).build();
		ChatClient client = chatClientWith(advisor);

		// SSN present but PII check is off — should not throw
		ChatResponse response = client.prompt().user("My SSN is 123-45-6789").call().chatResponse();

		assertThat(response).isNotNull();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static ChatResponse buildResponse(long totalTokens) {
		int total = (int) totalTokens;
		int half = total / 2;
		return ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("ok"))))
			.metadata(org.springframework.ai.chat.metadata.ChatResponseMetadata.builder()
				.usage(new org.springframework.ai.chat.metadata.DefaultUsage(half, half, total))
				.build())
			.build();
	}

}
