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

package org.springframework.ai.openai.responses;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseCreatedEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.services.async.ResponseServiceAsync;
import com.openai.services.blocking.ResponseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dimitar Proynov
 */
@ExtendWith(MockitoExtension.class)
class OpenAiResponsesChatModelTests {

	@Mock
	private OpenAIClient openAiClient;

	@Mock
	private OpenAIClientAsync openAiClientAsync;

	@Mock
	private ResponseService responseService;

	@Mock
	private ResponseServiceAsync responseServiceAsync;

	private OpenAiResponsesChatModel model(OpenAiResponsesChatOptions options) {
		return OpenAiResponsesChatModel.builder()
			.openAiClient(this.openAiClient)
			.openAiClientAsync(this.openAiClientAsync)
			.options(options)
			.build();
	}

	private void givenResponse(String fixture) {
		when(this.openAiClient.responses()).thenReturn(this.responseService);
		when(this.responseService.create(any(ResponseCreateParams.class), any(RequestOptions.class)))
			.thenReturn(ResponsesTestFixtures.response(fixture));
	}

	@Test
	void callMapsTheResponseOntoAChatResponse() {
		givenResponse("text-with-reasoning-summary.json");

		ChatResponse response = model(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build())
			.call(new Prompt("Weather in Paris?"));

		AssistantMessage message = response.getResult().getOutput();
		assertThat(message.getText()).isEqualTo("It is 18 degrees in Paris.");
		// One part per output item: the reasoning item, then the message item
		assertThat(message.getParts()).hasSize(2);
		assertThat(message.getReasoning()).singleElement()
			.satisfies(reasoning -> assertThat(reasoning.payload()).isNotNull());
		assertThat(response.getMetadata().getId()).isEqualTo("resp_text_1");
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(225);
	}

	/**
	 * Everything a turn reports beyond its parts lives on the generation and response
	 * metadata. Nothing is put on the assistant message, whose metadata some chat memory
	 * repositories persist and others drop, and which nothing on the replay path reads.
	 */
	@Test
	void theAssistantMessageCarriesNoMetadataOfItsOwn() {
		givenResponse("text-with-reasoning-summary.json");

		ChatResponse response = model(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build())
			.call(new Prompt("Weather in Paris?"));

		// AbstractMessage always stamps the message type; nothing else is added
		assertThat(response.getResult().getOutput().getMetadata()).containsOnlyKeys(AbstractMessage.MESSAGE_TYPE);
		assertThat(response.getResult().getMetadata().<String>get(OpenAiResponsesMetadata.STATUS))
			.isEqualTo("completed");
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
	}

	/**
	 * The tool loop belongs to {@code ToolCallingAdvisor}. A response asking for a tool
	 * must come straight back, with exactly one call to OpenAI.
	 */
	@Test
	void callDoesNotRunATooLoopOfItsOwn() {
		givenResponse("reasoning-with-function-call.json");

		ChatResponse response = model(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build())
			.call(new Prompt("Weather in Paris?"));

		assertThat(response.hasToolCalls()).isTrue();
		verify(this.responseService, times(1)).create(any(ResponseCreateParams.class), any(RequestOptions.class));
	}

	@Test
	void callRaisesOnAFailedResponseRatherThanReturningAnEmptyAnswer() {
		givenResponse("failed.json");

		assertThatThrownBy(
				() -> model(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build()).call(new Prompt("Hi")))
			.isInstanceOf(OpenAiResponsesException.class)
			.hasMessageContaining("server_error");
	}

	@Test
	void runtimeOptionsAreMergedOverTheModelDefaults() {
		givenResponse("text-with-reasoning-summary.json");
		var defaults = OpenAiResponsesChatOptions.builder().model("gpt-5-mini").reasoningEffort("high").build();

		model(defaults)
			.call(new Prompt(List.of(new UserMessage("Hi")), ChatOptions.builder().temperature(0.25).build()));

		ResponseCreateParams request = captureRequest();
		assertThat(request.temperature()).contains(0.25);
		assertThat(request.reasoning().flatMap(reasoning -> reasoning.effort()).map(effort -> effort.asString()))
			.contains("high");
	}

	/**
	 * Native runtime options are a complete specification of the request and replace the
	 * model's defaults rather than merging with them, which is what
	 * {@code OpenAiChatModel} does too. {@code mutate()} is the way to override one
	 * setting and keep the rest.
	 */
	@Test
	void nativeRuntimeOptionsReplaceTheModelDefaultsRatherThanMergingWithThem() {
		givenResponse("text-with-reasoning-summary.json");
		var defaults = OpenAiResponsesChatOptions.builder().model("gpt-5").reasoningEffort("high").build();

		model(defaults).call(new Prompt(List.of(new UserMessage("Hi")),
				OpenAiResponsesChatOptions.builder().temperature(0.25).build()));

		ResponseCreateParams request = captureRequest();
		assertThat(request.temperature()).contains(0.25);
		// Neither the model nor the reasoning effort of the defaults survives: the
		// runtime
		// options fall back to their own defaults for everything they leave unset
		assertThat(request.reasoning()).isEmpty();
		assertThat(request.model().flatMap(ResponsesModel::string))
			.contains(OpenAiResponsesChatOptions.DEFAULT_CHAT_MODEL);
	}

	/**
	 * Mutating the model's own options is the supported way to override one setting, and
	 * keeps every other default.
	 */
	@Test
	void mutatingTheModelOptionsKeepsTheDefaultsItDoesNotOverride() {
		givenResponse("text-with-reasoning-summary.json");
		var defaults = OpenAiResponsesChatOptions.builder().model("gpt-5").reasoningEffort("high").build();

		model(defaults).call(new Prompt(List.of(new UserMessage("Hi")), defaults.mutate().temperature(0.25).build()));

		ResponseCreateParams request = captureRequest();
		assertThat(request.temperature()).contains(0.25);
		assertThat(request.reasoning().flatMap(reasoning -> reasoning.effort()).map(effort -> effort.asString()))
			.contains("high");
		assertThat(request.model().flatMap(ResponsesModel::string)).contains("gpt-5");
	}

	private ResponseCreateParams captureRequest() {
		ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
		verify(this.responseService).create(captor.capture(), any(RequestOptions.class));
		return captor.getValue();
	}

	@Test
	void streamMapsEventsAndAggregatesThemIntoOneMessage() {
		Response response = ResponsesTestFixtures.response("text-with-reasoning-summary.json");
		givenStream(List.of(
				ResponseStreamEvent
					.ofCreated(ResponseCreatedEvent.builder().response(response).sequenceNumber(0).build()),
				ResponseStreamEvent.ofOutputTextDelta(ResponseTextDeltaEvent.builder()
					.contentIndex(0)
					.delta("It is 18 degrees in Paris.")
					.itemId("msg_1")
					// The message item sits at output index 1 in this fixture, behind the
					// reasoning item, and the output index is the content block index.
					.outputIndex(1)
					.logprobs(List.of())
					.sequenceNumber(1)
					.build()),
				ResponseStreamEvent
					.ofCompleted(ResponseCompletedEvent.builder().response(response).sequenceNumber(2).build())));

		List<ChatResponse> chunks = model(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build())
			.stream(new Prompt("Weather in Paris?"))
			.collectList()
			.block();

		assertThat(chunks).isNotNull();
		// The text is emitted once, by the delta chunk: the terminal chunk does not
		// repeat a block the deltas already carried.
		assertThat(chunks.stream().map(chunk -> chunk.getResult().getOutput().getText()).reduce("", String::concat))
			.isEqualTo("It is 18 degrees in Paris.");
	}

	private void givenStream(List<ResponseStreamEvent> events) {
		when(this.openAiClientAsync.responses()).thenReturn(this.responseServiceAsync);
		when(this.responseServiceAsync.createStreaming(any(ResponseCreateParams.class), any(RequestOptions.class)))
			.thenReturn(new ScriptedStreamResponse(events));
	}

	/**
	 * A hand-rolled {@link AsyncStreamResponse} that replays a recorded event list, so
	 * the Reactor plumbing is exercised without a network call.
	 */
	private record ScriptedStreamResponse(
			List<ResponseStreamEvent> events) implements AsyncStreamResponse<ResponseStreamEvent> {

		@Override
		public AsyncStreamResponse<ResponseStreamEvent> subscribe(
				AsyncStreamResponse.Handler<? super ResponseStreamEvent> handler) {
			this.events.forEach(handler::onNext);
			return this;
		}

		@Override
		public AsyncStreamResponse<ResponseStreamEvent> subscribe(
				AsyncStreamResponse.Handler<? super ResponseStreamEvent> handler, Executor executor) {
			return subscribe(handler);
		}

		@Override
		public CompletableFuture<Void> onCompleteFuture() {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void close() {
		}

	}

}
