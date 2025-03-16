/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.watsonx;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.watsonx.api.WatsonxAiApi;
import org.springframework.ai.watsonx.api.WatsonxAiChatRequest;
import org.springframework.ai.watsonx.api.WatsonxAiChatResponse;
import org.springframework.ai.watsonx.api.WatsonxAiChatResults;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author Pablo Sanchidrian Herrera
 * @author John Jairo Moreno Rojas
 * @author Alexandros Pappas
 */
public class WatsonxAiChatModelTest {

	WatsonxAiChatModel chatModel = new WatsonxAiChatModel(mock(WatsonxAiApi.class));

	@Test
	public void testCreateRequestWithNoModelId() {
		var options = ChatOptions.builder().temperature(0.9).topK(100).topP(0.6).build();

		Prompt prompt = new Prompt("Test message", options);

		Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> this.chatModel.request(prompt));
	}

	@Test
	public void testCreateRequestSuccessfullyWithDefaultParams() {

		String msg = "Test message";

		WatsonxAiChatOptions modelOptions = WatsonxAiChatOptions.builder().model("meta-llama/llama-2-70b-chat").build();
		Prompt prompt = new Prompt(msg, modelOptions);

		WatsonxAiChatRequest request = this.chatModel.request(prompt);

		Assert.assertEquals(request.getModelId(), "meta-llama/llama-2-70b-chat");
		assertThat(request.getParameters().get("decoding_method")).isEqualTo("greedy");
		assertThat(request.getParameters().get("temperature")).isEqualTo(0.7);
		assertThat(request.getParameters().get("top_p")).isEqualTo(1.0);
		assertThat(request.getParameters().get("top_k")).isEqualTo(50);
		assertThat(request.getParameters().get("max_new_tokens")).isEqualTo(20);
		assertThat(request.getParameters().get("min_new_tokens")).isEqualTo(0);
		assertThat(request.getParameters().get("stop_sequences")).isInstanceOf(List.class);
		Assert.assertEquals(request.getParameters().get("stop_sequences"), List.of());
		assertThat(request.getParameters().get("random_seed")).isNull();
	}

	@Test
	public void testCreateRequestSuccessfullyWithNonDefaultParams() {

		String msg = "Test message";

		WatsonxAiChatOptions modelOptions = WatsonxAiChatOptions.builder()
			.model("meta-llama/llama-2-70b-chat")
			.decodingMethod("sample")
			.temperature(0.1)
			.topP(0.2)
			.topK(10)
			.maxNewTokens(30)
			.minNewTokens(10)
			.repetitionPenalty(1.4)
			.stopSequences(List.of("\n\n\n"))
			.randomSeed(4)
			.build();

		Prompt prompt = new Prompt(msg, modelOptions);

		WatsonxAiChatRequest request = this.chatModel.request(prompt);

		Assert.assertEquals(request.getModelId(), "meta-llama/llama-2-70b-chat");
		assertThat(request.getParameters().get("decoding_method")).isEqualTo("sample");
		assertThat(request.getParameters().get("temperature")).isEqualTo(0.1);
		assertThat(request.getParameters().get("top_p")).isEqualTo(0.2);
		assertThat(request.getParameters().get("top_k")).isEqualTo(10);
		assertThat(request.getParameters().get("max_new_tokens")).isEqualTo(30);
		assertThat(request.getParameters().get("min_new_tokens")).isEqualTo(10);
		assertThat(request.getParameters().get("stop_sequences")).isInstanceOf(List.class);
		Assert.assertEquals(request.getParameters().get("stop_sequences"), List.of("\n\n\n"));
		assertThat(request.getParameters().get("random_seed")).isEqualTo(4);
	}

	@Test
	public void testCreateRequestSuccessfullyWithChatDisabled() {

		String msg = "Test message";

		WatsonxAiChatOptions modelOptions = WatsonxAiChatOptions.builder()
			.model("meta-llama/llama-2-70b-chat")
			.decodingMethod("sample")
			.temperature(0.1)
			.topP(0.2)
			.topK(10)
			.maxNewTokens(30)
			.minNewTokens(10)
			.repetitionPenalty(1.4)
			.stopSequences(List.of("\n\n\n"))
			.randomSeed(4)
			.build();

		Prompt prompt = new Prompt(msg, modelOptions);

		WatsonxAiChatRequest request = this.chatModel.request(prompt);

		Assert.assertEquals(request.getModelId(), "meta-llama/llama-2-70b-chat");
		assertThat(request.getInput()).isEqualTo(msg);
		assertThat(request.getParameters().get("decoding_method")).isEqualTo("sample");
		assertThat(request.getParameters().get("temperature")).isEqualTo(0.1);
		assertThat(request.getParameters().get("top_p")).isEqualTo(0.2);
		assertThat(request.getParameters().get("top_k")).isEqualTo(10);
		assertThat(request.getParameters().get("max_new_tokens")).isEqualTo(30);
		assertThat(request.getParameters().get("min_new_tokens")).isEqualTo(10);
		assertThat(request.getParameters().get("stop_sequences")).isInstanceOf(List.class);
		Assert.assertEquals(request.getParameters().get("stop_sequences"), List.of("\n\n\n"));
		assertThat(request.getParameters().get("random_seed")).isEqualTo(4);
	}

	@Test
	public void testCallMethod() {
		WatsonxAiApi mockChatApi = mock(WatsonxAiApi.class);
		WatsonxAiChatModel chatModel = new WatsonxAiChatModel(mockChatApi);

		Prompt prompt = new Prompt(List.of(new SystemMessage("Your prompt here")),
				WatsonxAiChatOptions.builder().model("google/flan-ul2").build());

		WatsonxAiChatOptions parameters = WatsonxAiChatOptions.builder().model("google/flan-ul2").build();

		WatsonxAiChatResults fakeResults = new WatsonxAiChatResults("LLM response", 4, 3, "max_tokens");

		WatsonxAiChatResponse fakeResponse = new WatsonxAiChatResponse("google/flan-ul2", new Date(),
				List.of(fakeResults),
				Map.of("warnings", List.of(Map.of("message", "the message", "id", "disclaimer_warning"))));

		given(mockChatApi.generate(any(WatsonxAiChatRequest.class)))
			.willReturn(ResponseEntity.of(Optional.of(fakeResponse)));

		Generation expectedGenerator = new Generation(new AssistantMessage("LLM response"),
				ChatGenerationMetadata.builder()
					.finishReason("max_tokens")
					.metadata("system",
							Map.of("warnings", List.of(Map.of("message", "the message", "id", "disclaimer_warning"))))
					.build());

		ChatResponse expectedResponse = new ChatResponse(List.of(expectedGenerator));
		ChatResponse response = chatModel.call(prompt);

		Assert.assertEquals(expectedResponse.getResults().size(), response.getResults().size());
		Assert.assertEquals(expectedResponse.getResult().getOutput(), response.getResult().getOutput());
	}

	@Test
	public void testStreamMethod() {
		WatsonxAiApi mockChatApi = mock(WatsonxAiApi.class);
		WatsonxAiChatModel chatModel = new WatsonxAiChatModel(mockChatApi);

		Prompt prompt = new Prompt(List.of(new SystemMessage("Your prompt here")),
				WatsonxAiChatOptions.builder().model("google/flan-ul2").build());

		WatsonxAiChatOptions parameters = WatsonxAiChatOptions.builder().model("google/flan-ul2").build();

		WatsonxAiChatResults fakeResultsFirst = new WatsonxAiChatResults("LLM resp", 0, 0, "max_tokens");
		WatsonxAiChatResults fakeResultsSecond = new WatsonxAiChatResults("onse", 4, 3, "not_finished");

		WatsonxAiChatResponse fakeResponseFirst = new WatsonxAiChatResponse("google/flan-ul2", new Date(),
				List.of(fakeResultsFirst),
				Map.of("warnings", List.of(Map.of("message", "the message", "id", "disclaimer_warning"))));
		WatsonxAiChatResponse fakeResponseSecond = new WatsonxAiChatResponse("google/flan-ul2", new Date(),
				List.of(fakeResultsSecond), null);

		Flux<WatsonxAiChatResponse> fakeResponse = Flux.just(fakeResponseFirst, fakeResponseSecond);
		given(mockChatApi.generateStreaming(any(WatsonxAiChatRequest.class))).willReturn(fakeResponse);

		Generation firstGen = new Generation(new AssistantMessage("LLM resp"),
				ChatGenerationMetadata.builder()
					.finishReason("max_tokens")
					.metadata("system",
							Map.of("warnings", List.of(Map.of("message", "the message", "id", "disclaimer_warning"))))
					.build());
		Generation secondGen = new Generation(new AssistantMessage("onse"));

		Flux<ChatResponse> response = chatModel.stream(prompt);

		StepVerifier.create(response).assertNext(current -> {

			ChatResponse expected = new ChatResponse(List.of(firstGen));

			Assert.assertEquals(expected.getResults().size(), current.getResults().size());
			Assert.assertEquals(expected.getResult().getOutput(), current.getResult().getOutput());
		}).assertNext(current -> {
			ChatResponse expected = new ChatResponse(List.of(secondGen));

			Assert.assertEquals(expected.getResults().size(), current.getResults().size());
			Assert.assertEquals(expected.getResult().getOutput(), current.getResult().getOutput());
		}).expectComplete().verify();

	}

}
