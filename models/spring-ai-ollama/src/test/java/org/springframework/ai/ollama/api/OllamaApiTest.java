package org.springframework.ai.ollama.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.ollama.api.OllamaApi.ChatResponse;
import static org.springframework.ai.ollama.api.OllamaApi.Message;
import static org.springframework.ai.ollama.api.OllamaApi.Message.Role.ASSISTANT;
import static org.springframework.ai.ollama.api.OllamaApi.Message.ToolCall;
import static org.springframework.ai.ollama.api.OllamaApi.Message.ToolCallFunction;

public class OllamaApiTest {

	@Test
	void chatResponseSerializationRoundTrip() throws JsonProcessingException {
		var objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());

		var original = new ChatResponse("aModel", Instant.now(),
				new Message(ASSISTANT, "someContent", List.of("anImage"),
						List.of(new ToolCall(new ToolCallFunction("functionName", Map.of("paramName", "paramValue"))))),
				"weAreDone", true, Duration.ofSeconds(7), Duration.ofSeconds(1), 23, Duration.ofSeconds(2), 56,
				Duration.ofSeconds(4));

		var serialized = objectMapper.writeValueAsString(original);
		var deserialized = objectMapper.readValue(serialized, ChatResponse.class);

		assertThat(deserialized).isEqualTo(original);
	}

}
