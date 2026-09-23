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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ModerationAdvisor}.
 *
 * @author Kim Taewoong
 */
@ExtendWith(MockitoExtension.class)
class ModerationAdvisorTests {

	@Mock
	private ModerationModel moderationModel;

	@Mock
	private CallAdvisorChain callAdvisorChain;

	@Mock
	private StreamAdvisorChain streamAdvisorChain;

	@Test
	void whenModerationModelIsNullThenThrow() {
		assertThatThrownBy(() -> ModerationAdvisor.builder().moderationModel(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("moderationModel must not be null");
	}

	@Test
	void whenModerationFlagsRequestThenBlockCall() {
		ModerationAdvisor advisor = advisor(true);
		ChatClientRequest request = request("harmful content");
		ModerationResponse moderationResponse = moderationResponse(true);
		given(this.moderationModel.call(any())).willReturn(moderationResponse);

		ChatClientResponse response = advisor.adviseCall(request, this.callAdvisorChain);

		verify(this.callAdvisorChain, never()).nextCall(any());
		assertThat(response.context().get(ModerationAdvisor.MODERATION_BLOCKED_CONTEXT_KEY)).isEqualTo(true);
		assertThat(response.context().get(ModerationAdvisor.MODERATION_RESPONSE_CONTEXT_KEY))
			.isEqualTo(moderationResponse);
		assertThat(response.chatResponse()).isNotNull();
		assertThat(response.chatResponse().getResult()).isNotNull();
		assertThat(response.chatResponse().getResult().getOutput().getText())
			.isEqualTo("I'm unable to respond to that due to safety policy.");
	}

	@Test
	void whenModerationAllowsRequestThenContinueCallChain() {
		ModerationAdvisor advisor = advisor(true);
		ChatClientRequest request = request("safe content");
		ModerationResponse moderationResponse = moderationResponse(false);
		ChatClientResponse expected = chatClientResponse("allowed");

		given(this.moderationModel.call(any())).willReturn(moderationResponse);
		given(this.callAdvisorChain.nextCall(any())).willReturn(expected);

		ChatClientResponse response = advisor.adviseCall(request, this.callAdvisorChain);

		ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(this.callAdvisorChain).nextCall(requestCaptor.capture());
		assertThat(requestCaptor.getValue().context().get(ModerationAdvisor.MODERATION_BLOCKED_CONTEXT_KEY))
			.isEqualTo(false);
		assertThat(requestCaptor.getValue().context().get(ModerationAdvisor.MODERATION_RESPONSE_CONTEXT_KEY))
			.isEqualTo(moderationResponse);
		assertThat(response).isEqualTo(expected);
	}

	@Test
	void whenModerationFailsAndFailOpenThenContinueCallChain() {
		ModerationAdvisor advisor = advisor(true);
		ChatClientRequest request = request("safe content");
		ChatClientResponse expected = chatClientResponse("allowed");

		given(this.moderationModel.call(any())).willThrow(new RuntimeException("moderation down"));
		given(this.callAdvisorChain.nextCall(request)).willReturn(expected);

		ChatClientResponse response = advisor.adviseCall(request, this.callAdvisorChain);

		verify(this.callAdvisorChain).nextCall(request);
		assertThat(response).isEqualTo(expected);
	}

	@Test
	void whenModerationFailsAndFailOpenIsDisabledThenThrow() {
		ModerationAdvisor advisor = advisor(false);
		ChatClientRequest request = request("safe content");

		given(this.moderationModel.call(any())).willThrow(new RuntimeException("moderation down"));

		assertThatThrownBy(() -> advisor.adviseCall(request, this.callAdvisorChain))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Moderation processing failed");
		verify(this.callAdvisorChain, never()).nextCall(any());
	}

	@Test
	void whenModerationFlagsRequestThenBlockStream() {
		ModerationAdvisor advisor = advisor(true);
		ChatClientRequest request = request("harmful content");
		ModerationResponse moderationResponse = moderationResponse(true);
		given(this.moderationModel.call(any())).willReturn(moderationResponse);

		ChatClientResponse response = advisor.adviseStream(request, this.streamAdvisorChain).blockFirst();

		verify(this.streamAdvisorChain, never()).nextStream(any());
		assertThat(response).isNotNull();
		assertThat(response.context().get(ModerationAdvisor.MODERATION_BLOCKED_CONTEXT_KEY)).isEqualTo(true);
		assertThat(response.context().get(ModerationAdvisor.MODERATION_RESPONSE_CONTEXT_KEY))
			.isEqualTo(moderationResponse);
	}

	@Test
	void whenModerationAllowsRequestThenContinueStreamChain() {
		ModerationAdvisor advisor = advisor(true);
		ChatClientRequest request = request("safe content");
		ModerationResponse moderationResponse = moderationResponse(false);
		ChatClientResponse expected = chatClientResponse("stream allowed");

		given(this.moderationModel.call(any())).willReturn(moderationResponse);
		given(this.streamAdvisorChain.nextStream(any())).willReturn(Flux.just(expected));

		ChatClientResponse response = advisor.adviseStream(request, this.streamAdvisorChain).blockFirst();

		ArgumentCaptor<ChatClientRequest> requestCaptor = ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(this.streamAdvisorChain).nextStream(requestCaptor.capture());
		assertThat(requestCaptor.getValue().context().get(ModerationAdvisor.MODERATION_BLOCKED_CONTEXT_KEY))
			.isEqualTo(false);
		assertThat(requestCaptor.getValue().context().get(ModerationAdvisor.MODERATION_RESPONSE_CONTEXT_KEY))
			.isEqualTo(moderationResponse);
		assertThat(response).isEqualTo(expected);
	}

	@Test
	void whenModerationFailsAndFailOpenThenContinueStreamChain() {
		ModerationAdvisor advisor = advisor(true);
		ChatClientRequest request = request("safe content");
		ChatClientResponse expected = chatClientResponse("stream fallback");

		given(this.moderationModel.call(any())).willThrow(new RuntimeException("moderation down"));
		given(this.streamAdvisorChain.nextStream(request)).willReturn(Flux.just(expected));

		ChatClientResponse response = advisor.adviseStream(request, this.streamAdvisorChain).blockFirst();

		verify(this.streamAdvisorChain).nextStream(request);
		assertThat(response).isEqualTo(expected);
	}

	@Test
	void whenModerationFailsAndFailOpenDisabledThenStreamErrors() {
		ModerationAdvisor advisor = advisor(false);
		ChatClientRequest request = request("safe content");
		given(this.moderationModel.call(any())).willThrow(new RuntimeException("moderation down"));

		assertThatThrownBy(() -> advisor.adviseStream(request, this.streamAdvisorChain).blockFirst())
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Moderation processing failed");
		verify(this.streamAdvisorChain, never()).nextStream(any());
	}

	private ModerationAdvisor advisor(boolean failOpen) {
		return ModerationAdvisor.builder()
			.moderationModel(this.moderationModel)
			.scheduler(Schedulers.immediate())
			.failOpen(failOpen)
			.build();
	}

	private ChatClientRequest request(String text) {
		return ChatClientRequest.builder().prompt(new Prompt(text)).build();
	}

	private ChatClientResponse chatClientResponse(String text) {
		return ChatClientResponse.builder()
			.chatResponse(
					ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage(text)))).build())
			.build();
	}

	private ModerationResponse moderationResponse(boolean flagged) {
		ModerationResult result = ModerationResult.builder().flagged(flagged).build();
		Moderation moderation = Moderation.builder()
			.id("mod-1")
			.model("moderation-model")
			.results(List.of(result))
			.build();
		ModerationResponse moderationResponse = mock(ModerationResponse.class, RETURNS_DEEP_STUBS);
		given(moderationResponse.getResult().getOutput()).willReturn(moderation);
		return moderationResponse;
	}

}
