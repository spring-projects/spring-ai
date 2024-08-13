package org.springframework.ai.vertexai.anthropic.api;

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.vertexai.anthropic.model.*;
import org.springframework.ai.vertexai.anthropic.model.stream.EventType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class VertexAiAnthropicApiTest {

	private VertexAiAnthropicApi vertexAIAnthropicApi;

	private GoogleCredentials mockCredentials;

	private RestClient mockRestClient;

	private WebClient mockWebClient;

	@BeforeEach
	void setUp() {
		mockCredentials = mock(GoogleCredentials.class);
		mockRestClient = mock(RestClient.class);
		mockWebClient = mock(WebClient.class);
		vertexAIAnthropicApi = new VertexAiAnthropicApi.Builder().projectId("project-id")
			.location("location")
			.credentials(mockCredentials)
			.restClient(mockRestClient)
			.webClient(mockWebClient)
			.build();
	}

	@Test
	void chatCompletion_validRequest_returnsResponseEntity() throws IOException {
		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.withAnthropicVersion("vertex-2023-10-16")
			.withMaxTokens(100)
			.withMessages(List.of(new AnthropicMessage(List.of(new ContentBlock("Hello")), Role.USER)))
			.withTemperature(0.5f)
			.withTopP(1.0f)
			.withStream(false)
			.build();

		ChatCompletionResponse response = new StreamHelper.ChatCompletionResponseBuilder().withId("1")
			.withType("message")
			.withRole(Role.ASSISTANT)
			.withModel("claude-3-5-sonnet@20240620")
			.withStopReason("end_turn")
			.withStopSequence(null)
			.withUsage(new ApiUsage(100, 50))
			.withContent(List.of(new ContentBlock("Hello, how can I help you?")))
			.build();

		ResponseEntity<ChatCompletionResponse> expectedResponse = ResponseEntity.ok(response);

		// Mock the RestClient and its nested interfaces
		RestClient.RequestBodyUriSpec mockRequestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
		RestClient.RequestBodySpec mockRequestBodySpec = mock(RestClient.RequestBodySpec.class);
		RestClient.ResponseSpec mockResponseSpec = mock(RestClient.ResponseSpec.class);

		// Stub the methods to return the mock instances and expected response
		when(mockRestClient.post()).thenReturn(mockRequestBodyUriSpec);
		when(mockRequestBodyUriSpec.uri(anyString())).thenReturn(mockRequestBodySpec);
		when(mockRequestBodySpec.headers(any(Consumer.class))).thenReturn(mockRequestBodySpec);
		when(mockRequestBodySpec.body(anyString())).thenReturn(mockRequestBodySpec);
		when(mockRequestBodySpec.retrieve()).thenReturn(mockResponseSpec);
		when(mockResponseSpec.toEntity(ChatCompletionResponse.class)).thenReturn(expectedResponse);

		ResponseEntity<ChatCompletionResponse> responseEntity = vertexAIAnthropicApi.chatCompletion(request,
				"claude-3-5-sonnet@20240620");

		assertEquals(expectedResponse, responseEntity);
	}

	@Test
	void chatCompletionStream_validRequest_returnsFlux() {
		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.withAnthropicVersion("vertex-2023-10-16")
			.withMaxTokens(100)
			.withMessages(List.of(new AnthropicMessage(List.of(new ContentBlock("Hello")), Role.USER)))
			.withTemperature(0.5f)
			.withTopP(1.0f)
			.withStream(true)
			.build();

		List<String> mockResponseStrings = List.of(
				"{\"type\": \"message_start\", \"message\": {\"id\": \"msg_1nZdL29xx5MUA1yADyHTEsnR8uuvGzszyY\", \"type\": \"message\", \"role\": \"assistant\", \"content\": [], \"model\": \"claude-3-5-sonnet-20240620\", \"stop_reason\": null, \"stop_sequence\": null, \"usage\": {\"input_tokens\": 25, \"output_tokens\": 1}}}",
				"{\"type\": \"content_block_start\", \"index\": 0, \"content_block\": {\"type\": \"text\", \"text\": \"\"}}",
				"{\"type\": \"ping\"}",
				"{\"type\": \"content_block_delta\", \"index\": 0, \"delta\": {\"type\": \"text_delta\", \"text\": \"Hello\"}}",
				"{\"type\": \"content_block_delta\", \"index\": 0, \"delta\": {\"type\": \"text_delta\", \"text\": \"!\"}}",
				"{\"type\": \"content_block_stop\", \"index\": 0}",
				"{\"type\": \"message_delta\", \"delta\": {\"stop_reason\": \"end_turn\", \"stop_sequence\":null}, \"usage\": {\"output_tokens\": 15}}",
				"{\"type\": \"message_stop\"}");

		Flux<String> expectedFlux = Flux.fromIterable(mockResponseStrings);

		// Mock the WebClient and its nested interfaces
		WebClient.RequestBodyUriSpec mockRequestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
		WebClient.RequestBodySpec mockRequestBodySpec = mock(WebClient.RequestBodySpec.class);
		WebClient.RequestHeadersSpec mockRequestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
		WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);

		// Stub the methods to return the mock instances and expected response
		when(mockWebClient.post()).thenReturn(mockRequestBodyUriSpec);
		when(mockRequestBodyUriSpec.uri(anyString())).thenReturn(mockRequestBodySpec);
		when(mockRequestBodySpec.headers(any(Consumer.class))).thenReturn(mockRequestBodySpec);
		when(mockRequestBodySpec.body(any(Mono.class), eq(ChatCompletionRequest.class)))
			.thenReturn(mockRequestHeadersSpec);
		when(mockRequestHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
		when(mockResponseSpec.bodyToFlux(String.class)).thenReturn(expectedFlux);

		Flux<ChatCompletionResponse> responseFlux = vertexAIAnthropicApi.chatCompletionStream(request,
				"claude-3-5-sonnet@20240620");

		// concatenate alle the text contents from the responseFlux into a single string
		StringBuilder sb = new StringBuilder();
		responseFlux.subscribe(s -> {
			if (s.content().size() > 0) {
				sb.append(s.content().get(0).text());
			}
		});

		assertEquals("Hello!", sb.toString());
	}

	@Test
	void chatCompletionStream_validRequestWithTools_returnsFlux() {
		String toolInput = "{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\",\"description\":\"The city and state, e.g. San Francisco, CA\"}},\"required\":[\"location\"]}";
		Tool tool = new Tool("get_weather", "Get the current weather in a given location",
				ModelOptionsUtils.jsonToObject(toolInput, HashMap.class));

		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.withAnthropicVersion("vertex-2023-10-16")
			.withMaxTokens(100)
			.withMessages(List.of(new AnthropicMessage(List.of(new ContentBlock("Hello")), Role.USER)))
			.withTemperature(0.5f)
			.withTopP(1.0f)
			.withStream(true)
			.withTools(List.of(tool))
			.build();

		List<String> mockResponseStrings = List.of(
				"{\"type\":\"message_start\",\"message\":{\"id\":\"msg_014p7gG3wDgGV9EUtLvnow3U\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-3-5-sonnet@20240620\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":472,\"output_tokens\":2},\"content\":[],\"stop_reason\":null}}",
				"{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
				"{\"type\": \"ping\"}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Okay\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\",\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" let\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"'s\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" check\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" the\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" weather\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" for\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" San\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" Francisco\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\",\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" CA\"}}",
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\":\"}}",
				"{\"type\":\"content_block_stop\",\"index\":0}",
				"{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_01T1x1fJ34qAmk2tNTrN7Up6\",\"name\":\"get_weather\",\"input\":{}}}",
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\"}}",
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"location\\\":\"}}",
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\" \\\"San\"}}",
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\" Francisc\"}}",
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"o,\"}}",
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\" CA\\\"\"}}",
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\", \"}}",
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"unit\\\": \\\"fah\"}}",
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"renheit\\\"}\"}}",
				"{\"type\":\"content_block_stop\",\"index\":1}",
				"{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":89}}",
				"{\"type\":\"message_stop\"}");

		Flux<String> expectedFlux = Flux.fromIterable(mockResponseStrings);

		// Mock the WebClient and its nested interfaces
		WebClient.RequestBodyUriSpec mockRequestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
		WebClient.RequestBodySpec mockRequestBodySpec = mock(WebClient.RequestBodySpec.class);
		WebClient.RequestHeadersSpec mockRequestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
		WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);

		// Stub the methods to return the mock instances and expected response
		when(mockWebClient.post()).thenReturn(mockRequestBodyUriSpec);
		when(mockRequestBodyUriSpec.uri(anyString())).thenReturn(mockRequestBodySpec);
		when(mockRequestBodySpec.headers(any(Consumer.class))).thenReturn(mockRequestBodySpec);
		when(mockRequestBodySpec.body(any(Mono.class), eq(ChatCompletionRequest.class)))
			.thenReturn(mockRequestHeadersSpec);
		when(mockRequestHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
		when(mockResponseSpec.bodyToFlux(String.class)).thenReturn(expectedFlux);

		Flux<ChatCompletionResponse> responseFlux = vertexAIAnthropicApi.chatCompletionStream(request,
				"claude-3-5-sonnet@20240620");

		// concatenate alle the text contents from the responseFlux into a single string
		StringBuilder sb = new StringBuilder();
		responseFlux.subscribe(s -> {
			if (s.content().size() > 0) {
				if (s.content().get(0).type().equals(ContentBlock.Type.TEXT)
						|| s.content().get(0).type().equals(ContentBlock.Type.TEXT_DELTA)) {
					sb.append(s.content().get(0).text());
				}
				else if (s.type().equals(EventType.CONTENT_BLOCK_STOP.name())
						&& s.content().get(0).type().equals(ContentBlock.Type.TOOL_USE)) {
					sb.append(ModelOptionsUtils.toJsonString(s.content().get(0).input()));
				}
			}
		});

		assertEquals(
				"Okay, let's check the weather for San Francisco, CA:{\"unit\":\"fahrenheit\",\"location\":\"San Francisco, CA\"}",
				sb.toString());
	}

	@Test
	void chatCompletion_nullRequest_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> {
			vertexAIAnthropicApi.chatCompletion(null, "claude-3-5-sonnet@20240620");
		});
	}

	@Test
	void chatCompletionStream_nullRequest_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> {
			vertexAIAnthropicApi.chatCompletionStream(null, "claude-3-5-sonnet@20240620");
		});
	}

	@Test
	void chatCompletionStream_nonStreamingRequest_throwsException() {
		ChatCompletionRequest request = ChatCompletionRequest.builder()
			.withAnthropicVersion("vertex-2023-10-16")
			.withMaxTokens(100)
			.withMessages(List.of(new AnthropicMessage(List.of(new ContentBlock("Hello")), Role.USER)))
			.withTemperature(0.5f)
			.withTopP(1.0f)
			.withStream(false)
			.build();

		assertThrows(IllegalArgumentException.class, () -> {
			vertexAIAnthropicApi.chatCompletionStream(request, "claude-3-5-sonnet@20240620");
		});
	}

}
