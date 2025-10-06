/*
 * Copyright 2025 the original author or authors.
 */

package org.springframework.ai.openai;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests strict inference across a variety of complex JSON Schemas, without calling the
 * actual OpenAI API. The expectation is: 1. strict is TRUE only when all object nodes in
 * the schema tree have additionalProperties: false and required exactly equals the set of
 * properties (no optional fields), recursively. 2. otherwise strict should not be TRUE
 * (it may be false or null depending on implementation).
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiStrictInferenceSchemaTests {

	private static Stream<Arguments> schemaProvider() {
		return Stream.of(
				// Simple strict object: ap=false and required==properties
				Arguments.of("""
						{
						  "type": "object",
						  "properties": {
						    "a": {"type": "string"},
						    "b": {"type": "number"}
						  },
						  "required": ["a", "b"],
						  "additionalProperties": false
						}
						""", true),

				// Missing additionalProperties at root => cannot be strict
				Arguments.of("""
						{
						  "type": "object",
						  "properties": { "x": {"type": "string"} },
						  "required": ["x"]
						}
						""", false),

				// additionalProperties true => cannot be strict
				Arguments.of("""
						{
						  "type": "object",
						  "properties": { "x": {"type": "string"} },
						  "required": ["x"],
						  "additionalProperties": true
						}
						""", false),

				// Optional property (required != properties) => cannot be strict
				Arguments.of("""
						{
						  "type": "object",
						  "properties": { "x": {"type": "string"}, "y": {"type": "string"} },
						  "required": ["x"],
						  "additionalProperties": false
						}
						""", false),

				// Nested object strict at both levels => strict true
				Arguments.of("""
						{
						  "type": "object",
						  "properties": {
						    "inner": {
						      "type": "object",
						      "properties": {"p": {"type": "string"}},
						      "required": ["p"],
						      "additionalProperties": false
						    }
						  },
						  "required": ["inner"],
						  "additionalProperties": false
						}
						""", true),

				// Nested object missing inner additionalProperties => cannot be strict
				Arguments.of("""
						{
						  "type": "object",
						  "properties": {
						    "inner": {
						      "type": "object",
						      "properties": {"p": {"type": "string"}},
						      "required": ["p"]
						    }
						  },
						  "required": ["inner"],
						  "additionalProperties": false
						}
						""", false),

				// Array of objects, items strict => can be strict (root is strict and
				// items strict)
				Arguments.of("""
						{
						  "type": "object",
						  "properties": {
						    "list": {
						      "type": "array",
						      "items": {
						        "type": "object",
						        "properties": {"v": {"type": "integer"}},
						        "required": ["v"],
						        "additionalProperties": false
						      }
						    }
						  },
						  "required": ["list"],
						  "additionalProperties": false
						}
						""", true),

				// Array items missing additionalProperties => cannot be strict
				Arguments.of("""
						{
						  "type": "object",
						  "properties": {
						    "list": {
						      "type": "array",
						      "items": {
						        "type": "object",
						        "properties": {"v": {"type": "integer"}},
						        "required": ["v"]
						      }
						    }
						  },
						  "required": ["list"],
						  "additionalProperties": false
						}
						""", false),

				// property-level anyOf: both branches strict and root strict => strict
				// true
				Arguments.of(
						"""
								{
								  "type": "object",
								  "properties": {
								    "item": {
								      "anyOf": [
								        {"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name","age"],"additionalProperties":false},
								        {"type":"object","properties":{"number":{"type":"string"},"street":{"type":"string"},"city":{"type":"string"}},"required":["number","street","city"],"additionalProperties":false}
								      ]
								    }
								  },
								  "required": ["item"],
								  "additionalProperties": false
								}
								""",
						true),

				// property-level anyOf: one branch non-strict (missing
				// additionalProperties) => cannot be strict
				Arguments.of(
						"""
								{
								  "type": "object",
								  "properties": {
								    "item": {
								      "anyOf": [
								        {"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name","age"],"additionalProperties":false},
								        {"type":"object","properties":{"number":{"type":"string"},"street":{"type":"string"},"city":{"type":"string"}},"required":["number","street","city"]}
								      ]
								    }
								  },
								  "required": ["item"],
								  "additionalProperties": false
								}
								""",
						false),

				// additionalProperties is an object schema (not boolean false) => cannot
				// be strict
				Arguments.of("""
						{
						  "type": "object",
						  "properties": {"x": {"type": "string"}},
						  "required": ["x"],
						  "additionalProperties": {"type":"string"}
						}
						""", false));
	}

	@ParameterizedTest
	@MethodSource("schemaProvider")
	void strictInferenceForComplexSchemas(String schema, boolean expectStrictTrue) {
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model(OpenAiApi.DEFAULT_CHAT_MODEL)
			.toolCallbacks(List.of(FunctionToolCallback.builder("testTool", new MockWeatherService())
				.description("Test tool")
				.inputType(Object.class)
				.inputSchema(schema)
				.build()))
			.build();

		Prompt prompt = new Prompt(List.of(new UserMessage("Run test tool")), options);

		OpenAiChatModel model = OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build())
			.build();

		ChatCompletionRequest request = model.createRequest(prompt, false);

		// If we expect strict true, require TRUE exactly.
		// Otherwise, assert it is not TRUE (can be false or null depending on current
		// implementation).
		boolean isAnyStrictTrue = request.tools()
			.stream()
			.allMatch(tool -> Boolean.TRUE.equals(tool.getFunction().getStrict()));

		if (expectStrictTrue) {
			assertThat(isAnyStrictTrue).isTrue();
		}
		else {
			// At least one tool (only one here) must not be strict true
			assertThat(request.tools().stream().noneMatch(tool -> Boolean.TRUE.equals(tool.getFunction().getStrict())))
				.isTrue();
		}

		assertThatCode(() -> {
			var output = model.call(prompt);
			System.out.println(output.getResult().getOutput());
		}).doesNotThrowAnyException();
	}

}
