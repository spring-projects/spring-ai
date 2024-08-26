package org.springframework.ai.vertexai.anthropic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vertexai.anthropic.api.StreamHelper;
import org.springframework.ai.vertexai.anthropic.api.VertexAiAnthropicApi;
import org.springframework.ai.vertexai.anthropic.model.*;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VertexAiAnthropicChatModelTest {

	private VertexAiAnthropicApi anthropicApi;

	private VertexAiAnthropicChatModel chatModel;

	@BeforeEach
	void setUp() {
		anthropicApi = mock(VertexAiAnthropicApi.class);
		chatModel = new VertexAiAnthropicChatModel(anthropicApi);
	}

	@Test
	void call_withValidPrompt_returnsChatResponse() {
		Prompt prompt = new Prompt(List.of(new UserMessage("Hello")));
		ChatCompletionResponse response = new StreamHelper.ChatCompletionResponseBuilder().withId("1")
			.withType("message")
			.withRole(Role.ASSISTANT)
			.withModel("claude-3-5-sonnet@20240620")
			.withStopReason("end_turn")
			.withStopSequence(null)
			.withUsage(new ApiUsage(100, 50))
			.withContent(List.of(new ContentBlock("Hello, how can I help you?")))
			.build();

		when(anthropicApi.chatCompletion(any(ChatCompletionRequest.class), anyString()))
			.thenReturn(ResponseEntity.ok(response));

		ChatResponse chatResponse = chatModel.call(prompt);

		assertNotNull(chatResponse);
		assertEquals(1, chatResponse.getResults().size());
		assertEquals("Hello, how can I help you?", chatResponse.getResults().get(0).getOutput().getContent());
	}

	@Test
	void call_withValidPromptWithOptions_returnsChatResponse() {
		VertexAiAnthropicChatOptions options = VertexAiAnthropicChatOptions.builder()
			.withAnthropicVersion("vertex-2023-10-16")
			.withModel("claude-3-opus@20240229")
			.withMaxTokens(100)
			.build();
		Prompt prompt = new Prompt(new UserMessage("Hello"), options);
		ChatCompletionResponse response = new StreamHelper.ChatCompletionResponseBuilder().withId("1")
			.withType("message")
			.withRole(Role.ASSISTANT)
			.withModel("claude-3-opus@20240229")
			.withStopReason("end_turn")
			.withStopSequence(null)
			.withUsage(new ApiUsage(100, 50))
			.withContent(List.of(new ContentBlock("Hello, how can I help you?")))
			.build();

		when(anthropicApi.chatCompletion(any(ChatCompletionRequest.class), anyString()))
			.thenReturn(ResponseEntity.ok(response));

		ChatResponse chatResponse = chatModel.call(prompt);

		assertNotNull(chatResponse);
		assertEquals(1, chatResponse.getResults().size());
		assertEquals("Hello, how can I help you?", chatResponse.getResults().get(0).getOutput().getContent());
	}

	@Test
	void call_withValidPrompt_returnsChatResponseWithTools() {
		Prompt prompt = new Prompt(List.of(new UserMessage("What is the wheather today in Milan?")));
		ChatCompletionResponse response = new StreamHelper.ChatCompletionResponseBuilder().withId("1")
			.withType("message")
			.withRole(Role.ASSISTANT)
			.withModel("claude-3-5-sonnet@20240620")
			.withStopReason("end_turn")
			.withStopSequence(null)
			.withUsage(new ApiUsage(100, 50))
			.withContent(List.of(new ContentBlock(ContentBlock.Type.TOOL_USE, "1", "weather", new HashMap<>())))
			.build();

		when(anthropicApi.chatCompletion(any(ChatCompletionRequest.class), anyString()))
			.thenReturn(ResponseEntity.ok(response));

		ChatResponse chatResponse = chatModel.call(prompt);

		assertNotNull(chatResponse);
		assertEquals(1, chatResponse.getResults().size());
		assertEquals("weather", chatResponse.getResults().get(0).getOutput().getToolCalls().get(0).name());
	}

	@Test
	void call_withValidCompleteToolPrompt_returnsChatResponse() {
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("1", "type", "weather",
				"{\"city\": \"Milan\", \"when\": \"today\"}");

		List<Message> messages = List.of(new UserMessage("Hello"), new AssistantMessage("Hello, how can I help you?"),
				new UserMessage("What is the wheather today in Milan?"),
				new ToolResponseMessage(
						List.of(new ToolResponseMessage.ToolResponse("1", "weather", "20 degrees and sunny"))),
				new AssistantMessage("The weather in Milan is 20°C and sunny.", new HashMap<>(), List.of(toolCall)),
				new UserMessage("Thank you"));

		Prompt prompt = new Prompt(messages);
		ChatCompletionResponse response = new StreamHelper.ChatCompletionResponseBuilder().withId("1")
			.withType("message")
			.withRole(Role.ASSISTANT)
			.withModel("claude-3-5-sonnet@20240620")
			.withStopReason("end_turn")
			.withStopSequence(null)
			.withUsage(new ApiUsage(100, 50))
			.withContent(List.of(new ContentBlock("You're welcome!")))
			.build();

		when(anthropicApi.chatCompletion(any(ChatCompletionRequest.class), anyString()))
			.thenReturn(ResponseEntity.ok(response));

		ChatResponse chatResponse = chatModel.call(prompt);

		assertNotNull(chatResponse);
		assertEquals(1, chatResponse.getResults().size());
		assertEquals("You're welcome!", chatResponse.getResults().get(0).getOutput().getContent());
	}

	@Test
	void call_withValidPartialToolPrompt_returnsChatResponse() {
		List<Message> messages = List.of(new UserMessage("Hello"), new AssistantMessage("Hello, how can I help you?"),
				new UserMessage("What is the wheather today in Milan?"), new ToolResponseMessage(
						List.of(new ToolResponseMessage.ToolResponse("1", "weather", "20 degrees and sunny"))));

		Prompt prompt = new Prompt(messages);
		ChatCompletionResponse response = new StreamHelper.ChatCompletionResponseBuilder().withId("1")
			.withType("message")
			.withRole(Role.ASSISTANT)
			.withModel("claude-3-5-sonnet@20240620")
			.withStopReason("end_turn")
			.withStopSequence(null)
			.withUsage(new ApiUsage(100, 50))
			.withContent(List.of(new ContentBlock("The weather in Milan is 20°C and sunny.")))
			.build();

		when(anthropicApi.chatCompletion(any(ChatCompletionRequest.class), anyString()))
			.thenReturn(ResponseEntity.ok(response));

		ChatResponse chatResponse = chatModel.call(prompt);

		assertNotNull(chatResponse);
		assertEquals(1, chatResponse.getResults().size());
		assertEquals("The weather in Milan is 20°C and sunny.",
				chatResponse.getResults().get(0).getOutput().getContent());
	}

	@Test
	void stream_withValidPrompt_returnsFluxOfChatResponses() {
		Prompt prompt = new Prompt(List.of(new UserMessage("Stream this")));
		ChatCompletionResponse response = new StreamHelper.ChatCompletionResponseBuilder().withId("1")
			.withType("message")
			.withRole(Role.ASSISTANT)
			.withModel("claude-3-5-sonnet@20240620")
			.withStopReason("end_turn")
			.withStopSequence(null)
			.withUsage(new ApiUsage(100, 50))
			.withContent(List.of(new ContentBlock("Streaming response")))
			.build();

		when(anthropicApi.chatCompletionStream(any(ChatCompletionRequest.class), anyString()))
			.thenReturn(Flux.just(response));

		Flux<ChatResponse> chatResponseFlux = chatModel.stream(prompt);

		List<ChatResponse> chatResponses = chatResponseFlux.collectList().block();
		assertNotNull(chatResponses);
		assertEquals(1, chatResponses.size());
		assertEquals("Streaming response", chatResponses.get(0).getResults().get(0).getOutput().getContent());
	}

	@Test
	void call_withNullPrompt_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> chatModel.call((Prompt) null));
	}

	@Test
	void stream_withNullPrompt_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> chatModel.stream((Prompt) null));
	}

	@Test
	void call_withEmptyPrompt_throwsException() {
		Prompt prompt = new Prompt(List.of());
		assertThrows(IllegalArgumentException.class, () -> chatModel.call(prompt));
	}

	@Test
	void stream_withEmptyPrompt_throwsException() {
		Prompt prompt = new Prompt(List.of());
		assertThrows(IllegalArgumentException.class, () -> chatModel.stream(prompt));
	}

}
