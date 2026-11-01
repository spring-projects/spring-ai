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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.ResponseFormatText;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolChoiceFunction;
import com.openai.models.responses.ToolChoiceOptions;
import com.openai.models.responses.ToolChoiceTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Builds a {@link ResponseCreateParams} from a {@link Prompt} whose options have already
 * been merged.
 * <p>
 * Everything defers to the provider default unless the caller set it. There are exactly
 * three framework-level defaults, all for correctness rather than preference:
 * <ul>
 * <li>{@code store: false} is always sent, and is not configurable. This model is
 * stateless by design, so there is nothing for OpenAI to retain on its behalf. It is also
 * what makes the encrypted reasoning blob come back at all: with {@code store: true}
 * OpenAI keeps the reasoning server-side for {@code previous_response_id} to address, and
 * returns no reasoning to replay.
 * <li>{@code include: ["reasoning.encrypted_content"]} is always requested. Without the
 * encrypted reasoning blob coming back, replaying a reasoning turn is impossible, not
 * merely degraded.
 * <li>Tool schemas are sent as {@code strict: false} unless the caller says otherwise.
 * The Responses API attempts strict mode when the flag is omitted, and Spring AI's
 * generated schemas are frequently not strict-compatible, so omitting it would make the
 * same tool behave differently across the two OpenAI models.
 * </ul>
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 */
final class ResponsesRequestBuilder {

	private static final JsonMapper jsonMapper = ObjectMappers.jsonMapper();

	private ResponsesRequestBuilder() {
	}

	static ResponseCreateParams build(Prompt prompt, ToolCallingManager toolCallingManager) {
		OpenAiResponsesChatOptions options = (OpenAiResponsesChatOptions) prompt.getOptions();
		Assert.state(options != null, "ChatOptions must not be null");

		ResponsesItemMapper.Input input = ResponsesItemMapper.toInput(prompt.getInstructions());

		ResponseCreateParams.Builder builder = ResponseCreateParams.builder();

		// Microsoft Foundry addresses a deployment, plain OpenAI a model
		builder.model(options.getDeploymentName() != null ? options.getDeploymentName() : options.getModel());
		builder.inputOfResponse(input.items());
		if (input.instructions() != null) {
			builder.instructions(input.instructions());
		}

		builder.include(includes(options.getInclude()));

		if (options.getMaxOutputTokens() != null) {
			builder.maxOutputTokens(options.getMaxOutputTokens().longValue());
		}
		if (options.getTemperature() != null) {
			builder.temperature(options.getTemperature());
		}
		if (options.getTopP() != null) {
			builder.topP(options.getTopP());
		}

		Reasoning reasoning = reasoning(options);
		if (reasoning != null) {
			builder.reasoning(reasoning);
		}
		ResponseTextConfig text = text(options);
		if (text != null) {
			builder.text(text);
		}

		List<Tool> tools = tools(options, toolCallingManager);
		if (!tools.isEmpty()) {
			builder.tools(tools);
		}
		applyToolChoice(builder, options.getToolChoice());
		if (options.getParallelToolCalls() != null) {
			builder.parallelToolCalls(options.getParallelToolCalls());
		}
		if (options.getMaxToolCalls() != null) {
			builder.maxToolCalls(options.getMaxToolCalls().longValue());
		}

		// Only the stateless mode is supported.
		builder.store(false);
		applyTruncation(builder, options.getTruncation());
		if (options.getServiceTier() != null) {
			builder.serviceTier(ResponseCreateParams.ServiceTier.of(options.getServiceTier()));
		}
		if (options.getPromptCacheKey() != null) {
			builder.promptCacheKey(options.getPromptCacheKey());
		}
		if (options.getSafetyIdentifier() != null) {
			builder.safetyIdentifier(options.getSafetyIdentifier());
		}
		if (!CollectionUtils.isEmpty(options.getMetadata())) {
			ResponseCreateParams.Metadata.Builder metadata = ResponseCreateParams.Metadata.builder();
			options.getMetadata().forEach((key, value) -> metadata.putAdditionalProperty(key, JsonValue.from(value)));
			builder.metadata(metadata.build());
		}
		if (!CollectionUtils.isEmpty(options.getCustomHeaders())) {
			options.getCustomHeaders().forEach(builder::putAdditionalHeader);
		}
		if (!CollectionUtils.isEmpty(options.getExtraBody())) {
			options.getExtraBody()
				.forEach((key, value) -> builder.putAdditionalBodyProperty(key, JsonValue.from(value)));
		}

		return builder.build();
	}

	/**
	 * {@code truncation} is deprecated in the pinned SDK - OpenAI is steering callers to
	 * {@code context_management}, which is out of scope here - but it is still the only
	 * documented way to let the model drop items when the context window overflows, so
	 * the option stays and the deprecation is suppressed at this single call site.
	 */
	@SuppressWarnings("deprecation")
	private static void applyTruncation(ResponseCreateParams.Builder builder, @Nullable String truncation) {
		if (truncation != null) {
			builder.truncation(ResponseCreateParams.Truncation.of(truncation));
		}
	}

	/**
	 * The encrypted reasoning blob is always requested, and any caller-supplied field is
	 * unioned in rather than replacing it.
	 */
	private static List<ResponseIncludable> includes(@Nullable List<String> configured) {
		Set<String> values = new LinkedHashSet<>();
		values.add(ResponseIncludable.REASONING_ENCRYPTED_CONTENT.asString());
		if (configured != null) {
			values.addAll(configured);
		}
		return values.stream().map(ResponseIncludable::of).toList();
	}

	private static @Nullable Reasoning reasoning(OpenAiResponsesChatOptions options) {
		if (options.getReasoningEffort() == null && options.getReasoningSummary() == null) {
			return null;
		}
		Reasoning.Builder reasoning = Reasoning.builder();
		if (options.getReasoningEffort() != null) {
			reasoning.effort(ReasoningEffort.of(options.getReasoningEffort().toLowerCase(Locale.ROOT)));
		}
		if (options.getReasoningSummary() != null) {
			reasoning.summary(Reasoning.Summary.of(options.getReasoningSummary().toLowerCase(Locale.ROOT)));
		}
		return reasoning.build();
	}

	/**
	 * Structured output and verbosity both live under {@code text} in this API, where
	 * Chat Completions has {@code response_format} and {@code verbosity} at the top
	 * level.
	 */
	private static @Nullable ResponseTextConfig text(OpenAiResponsesChatOptions options) {
		OpenAiChatModel.ResponseFormat responseFormat = options.getResponseFormat();
		if (responseFormat == null && options.getVerbosity() == null) {
			return null;
		}
		ResponseTextConfig.Builder text = ResponseTextConfig.builder();
		if (options.getVerbosity() != null) {
			text.verbosity(ResponseTextConfig.Verbosity.of(options.getVerbosity().toLowerCase(Locale.ROOT)));
		}
		if (responseFormat != null) {
			text.format(format(responseFormat));
		}
		return text.build();
	}

	private static ResponseFormatTextConfig format(OpenAiChatModel.ResponseFormat responseFormat) {
		return switch (responseFormat.getType()) {
			case TEXT -> ResponseFormatTextConfig.ofText(ResponseFormatText.builder().build());
			case JSON_OBJECT -> ResponseFormatTextConfig.ofJsonObject(ResponseFormatJsonObject.builder().build());
			case JSON_SCHEMA -> {
				String jsonSchema = responseFormat.getJsonSchema() != null ? responseFormat.getJsonSchema() : "";
				try {
					ResponseFormatTextJsonSchemaConfig.Schema schema = jsonMapper.readValue(jsonSchema,
							ResponseFormatTextJsonSchemaConfig.Schema.class);
					Boolean strict = responseFormat.getStrict();
					yield ResponseFormatTextConfig.ofJsonSchema(ResponseFormatTextJsonSchemaConfig.builder()
						.name("json_schema")
						.schema(schema)
						.strict(strict != null ? strict : true)
						.build());
				}
				catch (Exception ex) {
					throw new IllegalArgumentException("Failed to parse JSON schema: " + jsonSchema, ex);
				}
			}
		};
	}

	private static List<Tool> tools(OpenAiResponsesChatOptions options, ToolCallingManager toolCallingManager) {
		List<Tool> tools = new ArrayList<>();
		// Tool definitions only; the loop itself belongs to ToolCallingAdvisor
		for (ToolDefinition toolDefinition : toolCallingManager.resolveToolDefinitions(options)) {
			tools.add(Tool.ofFunction(functionTool(toolDefinition, options.getStrict())));
		}
		if (options.getHostedTools() != null) {
			options.getHostedTools().forEach(hostedTool -> tools.add(hostedTool.toTool()));
		}
		return tools;
	}

	private static FunctionTool functionTool(ToolDefinition toolDefinition, @Nullable Boolean configuredStrict) {
		// See the class javadoc: explicit false, not "let the provider decide"
		boolean strict = configuredStrict != null && configuredStrict;
		FunctionTool.Parameters.Builder parameters = FunctionTool.Parameters.builder();
		if (!toolDefinition.inputSchema().isEmpty()) {
			try {
				Map<?, ?> schema = jsonMapper.readValue(toolDefinition.inputSchema(), Map.class);
				schema.forEach(
						(key, value) -> parameters.putAdditionalProperty(String.valueOf(key), JsonValue.from(value)));
			}
			catch (Exception ex) {
				// A tool sent with empty parameters would be called with {} and fail at a
				// much
				// less obvious place, so refuse the request instead
				throw new IllegalArgumentException("Failed to parse the input schema of tool " + toolDefinition.name()
						+ ": " + toolDefinition.inputSchema(), ex);
			}
		}
		return FunctionTool.builder()
			.name(toolDefinition.name())
			.description(toolDefinition.description())
			.parameters(parameters.build())
			.strict(strict)
			.build();
	}

	/**
	 * Accepts the same shapes as {@code OpenAiChatOptions#toolChoice}: the SDK type, one
	 * of the {@code auto}/{@code none}/{@code required} keywords, or a JSON object naming
	 * a function or a hosted tool type.
	 */
	private static void applyToolChoice(ResponseCreateParams.Builder builder, @Nullable Object toolChoice) {
		if (toolChoice == null) {
			return;
		}
		if (toolChoice instanceof ResponseCreateParams.ToolChoice choice) {
			builder.toolChoice(choice);
			return;
		}
		if (!(toolChoice instanceof String value)) {
			throw new IllegalArgumentException("Unsupported toolChoice type: " + toolChoice.getClass().getName());
		}
		switch (value) {
			case "auto" -> builder.toolChoice(ToolChoiceOptions.AUTO);
			case "none" -> builder.toolChoice(ToolChoiceOptions.NONE);
			case "required" -> builder.toolChoice(ToolChoiceOptions.REQUIRED);
			default -> {
				try {
					JsonNode node = JacksonUtils.getDefaultJsonMapper().readTree(value);
					String type = node.get("type").asString();
					if ("function".equals(type)) {
						JsonNode nameNode = node.has("name") ? node.get("name") : node.get("function").get("name");
						builder.toolChoice(ToolChoiceFunction.builder().name(nameNode.asString()).build());
					}
					else {
						// A hosted tool type, e.g. {"type":"web_search"}
						builder.toolChoice(ToolChoiceTypes.builder().type(ToolChoiceTypes.Type.of(type)).build());
					}
				}
				catch (Exception ex) {
					throw new IllegalArgumentException("Failed to parse toolChoice JSON: " + value, ex);
				}
			}
		}
	}

}
