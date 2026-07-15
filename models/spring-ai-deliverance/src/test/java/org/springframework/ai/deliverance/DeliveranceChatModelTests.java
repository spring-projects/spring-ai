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

package org.springframework.ai.deliverance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.teknek.deliverance.client.spring.model.ChatCompletionMessageToolCall;
import io.teknek.deliverance.client.spring.model.ChatCompletionMessageToolCallFunction;
import io.teknek.deliverance.client.spring.model.ChatCompletionResponseMessage;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionRequest;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionResponse;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionResponseChoicesInner;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deliverance.api.DeliveranceApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveranceChatModelTests {

	@Test
	void mapsPromptAndOptionsToRequest() {
		DeliveranceChatOptions options = DeliveranceChatOptions.builder()
			.model("edwardcapriolo/Qwen3-4B-JQ4")
			.temperature(0.0)
			.maxTokens(64)
			.topP(0.95)
			.topK(64)
			.seed(42)
			.logprobs(true)
			.topLogprobs(5)
			.xtcThreshold(0.5)
			.xtcProbability(0.1)
			.guidedRegex("TICKET-[0-9]{4}")
			.build();
		DeliveranceChatModel model = new DeliveranceChatModel(new NoopDeliveranceApi(), new ObjectMapper(), options);

		CreateChatCompletionRequest request = model.toRequest(
				new Prompt(List.of(new SystemMessage("You are concise."), new UserMessage("Create a ticket id."))),
				false);

		assertThat(request.getModel()).isEqualTo("edwardcapriolo/Qwen3-4B-JQ4");
		assertThat(request.getStream()).isFalse();
		assertThat(request.getMessages().get(0).getRole()).isEqualTo("system");
		assertThat(request.getMessages().get(1).getRole()).isEqualTo("user");
		assertThat(request.getTemperature().doubleValue()).isEqualTo(0.0);
		assertThat(request.getMaxTokens()).isEqualTo(64);
		assertThat(request.getTopP().doubleValue()).isEqualTo(0.95);
		assertThat(request.getTopK().intValue()).isEqualTo(64);
		assertThat(request.getSeed()).isEqualTo(42);
		assertThat(request.getLogprobs()).isTrue();
		assertThat(request.getTopLogprobs()).isEqualTo(5);
		assertThat(request.getXtcThreshold().doubleValue()).isEqualTo(0.5);
		assertThat(request.getXtcProbability().doubleValue()).isEqualTo(0.1);
		assertThat(request.getGuidedRegex()).isEqualTo("TICKET-[0-9]{4}");
	}

	@Test
	void mapsToolCallbacksToDeliveranceTools() {
		DeliveranceChatOptions options = DeliveranceChatOptions.builder()
			.model("test-model")
			.toolCallbacks(new WeatherToolCallback())
			.build();
		DeliveranceChatModel model = new DeliveranceChatModel(new NoopDeliveranceApi(), new ObjectMapper(), options);

		CreateChatCompletionRequest request = model.toRequest(new Prompt("weather"), false);

		assertThat(request.getTools()).hasSize(1);
		assertThat(request.getTools().get(0).getType()).isEqualTo("function");
		assertThat(request.getTools().get(0).getFunction().getName()).isEqualTo("getWeather");
		assertThat(request.getParallelToolCalls()).isFalse();
	}

	@Test
	void streamsResponses() {
		DeliveranceChatModel model = new DeliveranceChatModel(new NoopDeliveranceApi(), new ObjectMapper(),
				DeliveranceChatOptions.builder().model("test-model").build());

		List<ChatResponse> responses = model.stream(new Prompt("hello")).collectList().block();

		assertThat(responses).isNotNull();
		assertThat(responses).hasSize(1);
		assertThat(responses.get(0).getResult().getOutput().getText()).isEqualTo("hello");
	}

	@Test
	void functionCallTest() {
		UserMessage userMessage = new UserMessage("What are the weather conditions in San Francisco?");
		List<Message> messages = new ArrayList<>(List.of(userMessage));
		var promptOptions = DeliveranceChatOptions.builder()
			.model("test-model")
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Find the current weather conditions and temperatures for a location.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();
		RecordingDeliveranceApi deliveranceApi = new RecordingDeliveranceApi();
		DeliveranceChatModel model = new DeliveranceChatModel(deliveranceApi, new ObjectMapper(),
				DeliveranceChatOptions.builder().model("test-model").build());
		ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
		Prompt prompt = new Prompt(messages, promptOptions);

		ChatResponse response = model.call(prompt);
		while (response.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
			response = model.call(prompt);
		}

		assertThat(response.getResult().getOutput().getText()).contains("30");
		assertThat(deliveranceApi.requests).hasSize(2);
		CreateChatCompletionRequest secondRequest = deliveranceApi.requests.get(1);
		assertThat(secondRequest.getMessages()).hasSize(3);
		assertThat(secondRequest.getMessages().get(0).getRole()).isEqualTo("user");
		assertThat(secondRequest.getMessages().get(1).getRole()).isEqualTo("assistant");
		assertThat(secondRequest.getMessages().get(1).getToolCalls()).hasSize(1);
		assertThat(secondRequest.getMessages().get(2).getRole()).isEqualTo("tool");
		assertThat(secondRequest.getMessages().get(2).getToolCallId()).isEqualTo("call_1");
		assertThat(secondRequest.getMessages().get(2).getContent()).contains("30");
	}

	@Test
	void runtimeDeliveranceOptionsMergeWithDefaults() {
		DeliveranceChatOptions defaults = DeliveranceChatOptions.builder()
			.model("default-model")
			.temperature(0.7)
			.maxTokens(128)
			.topP(0.9)
			.topK(40)
			.seed(42)
			.xtcThreshold(0.5)
			.guidedRegex("DEFAULT-[0-9]+")
			.build();
		DeliveranceChatOptions runtime = DeliveranceChatOptions.builder().temperature(0.0).maxTokens(64).build();
		DeliveranceChatModel model = new DeliveranceChatModel(new NoopDeliveranceApi(), new ObjectMapper(), defaults);

		CreateChatCompletionRequest request = model.toRequest(new Prompt("hello", runtime), false);

		assertThat(request.getModel()).isEqualTo("default-model");
		assertThat(request.getTemperature().doubleValue()).isEqualTo(0.0);
		assertThat(request.getMaxTokens()).isEqualTo(64);
		assertThat(request.getTopP().doubleValue()).isEqualTo(0.9);
		assertThat(request.getTopK().intValue()).isEqualTo(40);
		assertThat(request.getSeed()).isEqualTo(42);
		assertThat(request.getXtcThreshold().doubleValue()).isEqualTo(0.5);
		assertThat(request.getGuidedRegex()).isEqualTo("DEFAULT-[0-9]+");
	}

	@Test
	void serializesRequestWithSpringGeneratedMapper() throws Exception {
		DeliveranceChatOptions options = DeliveranceChatOptions.builder()
			.model("edwardcapriolo/Qwen3-4B-JQ4")
			.temperature(0.0)
			.maxTokens(64)
			.topK(64)
			.xtcThreshold(0.5)
			.guidedJson("""
					{"type":"object","properties":{"foo":{"type":"integer"}},"required":["foo"]}
					""")
			.build();
		DeliveranceChatModel model = new DeliveranceChatModel(new NoopDeliveranceApi(), new ObjectMapper(), options);

		CreateChatCompletionRequest request = model.toRequest(
				new Prompt(List.of(new SystemMessage("You are concise."), new UserMessage("Return foo as JSON."))),
				false);

		String json = DeliveranceApi.jsonMapper().writeValueAsString(request);

		assertThat(json).contains("\"model\":\"edwardcapriolo/Qwen3-4B-JQ4\"");
		assertThat(json).contains("\"role\":\"system\"");
		assertThat(json).contains("\"role\":\"user\"");
		assertThat(json).contains("\"top_k\":64");
		assertThat(json).contains("\"xtc_threshold\":0.5");
		assertThat(json).contains("\"guided_json\"");
		assertThat(json).contains("\"foo\"");
		assertThat(json).doesNotContain("\"stop\":null");
	}

	private static final class NoopDeliveranceApi implements DeliveranceApi {

		@Override
		public CreateChatCompletionResponse createChatCompletion(CreateChatCompletionRequest request) {
			return new CreateChatCompletionResponse().choices(List.of(new CreateChatCompletionResponseChoicesInner()
				.message(new ChatCompletionResponseMessage().content("hello"))));
		}

		@Override
		public Flux<ChatResponse> streamChatCompletion(CreateChatCompletionRequest request) {
			return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("hello")))));
		}

	}

	private static final class RecordingDeliveranceApi implements DeliveranceApi {

		private final List<CreateChatCompletionRequest> requests = new ArrayList<>();

		@Override
		public CreateChatCompletionResponse createChatCompletion(CreateChatCompletionRequest request) {
			this.requests.add(request);
			if (this.requests.size() == 1) {
				ChatCompletionMessageToolCallFunction function = new ChatCompletionMessageToolCallFunction()
					.name("getCurrentWeather")
					.arguments("{\"location\":\"San Francisco, CA\",\"unit\":\"C\"}");
				ChatCompletionMessageToolCall toolCall = new ChatCompletionMessageToolCall().id("call_1")
					.type("function")
					.function(function);
				ChatCompletionResponseMessage message = new ChatCompletionResponseMessage().content("")
					.role(ChatCompletionResponseMessage.RoleEnum.ASSISTANT)
					.toolCalls(List.of(toolCall));
				CreateChatCompletionResponseChoicesInner choice = new CreateChatCompletionResponseChoicesInner()
					.index(0)
					.finishReason(CreateChatCompletionResponseChoicesInner.FinishReasonEnum.TOOL_CALLS)
					.message(message);
				return new CreateChatCompletionResponse().choices(List.of(choice));
			}
			ChatCompletionResponseMessage message = new ChatCompletionResponseMessage()
				.content("The temperature in San Francisco is 30 C.")
				.role(ChatCompletionResponseMessage.RoleEnum.ASSISTANT);
			CreateChatCompletionResponseChoicesInner choice = new CreateChatCompletionResponseChoicesInner().index(0)
				.finishReason(CreateChatCompletionResponseChoicesInner.FinishReasonEnum.STOP)
				.message(message);
			return new CreateChatCompletionResponse().choices(List.of(choice));
		}

		@Override
		public Flux<ChatResponse> streamChatCompletion(CreateChatCompletionRequest request) {
			return Flux.empty();
		}

	}

	private static final class MockWeatherService
			implements Function<MockWeatherService.Request, MockWeatherService.Response> {

		@Override
		public Response apply(Request request) {
			return new Response(request.location().contains("San Francisco") ? 30 : 0, Unit.C);
		}

		enum Unit {

			C

		}

		record Request(String location, Unit unit) {

		}

		record Response(double temp, Unit unit) {

		}

	}

	private static final class WeatherToolCallback implements ToolCallback {

		@Override
		public ToolDefinition getToolDefinition() {
			return ToolDefinition.builder()
				.name("getWeather")
				.description("Get weather by city")
				.inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}")
				.build();
		}

		@Override
		public String call(String toolInput) {
			return "sunny";
		}

	}

}
