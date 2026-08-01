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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Advisor that validates the structured JSON output of a chat client response against a
 * JSON schema derived from the configured output type or a pre-supplied schema string.
 * <p>
 * When validation fails, the advisor appends the validation error to the user message and
 * re-invokes the model, repeating up to {@code maxRepeatAttempts} times.
 * <p>
 * Streaming responses are not supported.
 *
 * @author Christian Tzolov
 * @author Jewoo Shin
 */
public final class StructuredOutputValidationAdvisor implements CallAdvisor, StreamAdvisor {

	private static final Log logger = LogFactory.getLog(StructuredOutputValidationAdvisor.class);

	private final int advisorOrder;

	private final Schema jsonSchema;

	private final JsonMapper jsonMapper;

	private final int maxRepeatAttempts;

	private StructuredOutputValidationAdvisor(int advisorOrder, String outputJsonSchema, int maxRepeatAttempts,
			JsonMapper jsonMapper) {
		Assert.notNull(advisorOrder, "advisorOrder must not be null");
		Assert.notNull(outputJsonSchema, "outputJsonSchema must not be null");
		Assert.isTrue(advisorOrder > BaseAdvisor.HIGHEST_PRECEDENCE && advisorOrder < BaseAdvisor.LOWEST_PRECEDENCE,
				"advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");
		Assert.isTrue(maxRepeatAttempts >= 0, "repeatAttempts must be greater than or equal to 0");
		Assert.notNull(jsonMapper, "jsonMapper must not be null");

		this.advisorOrder = advisorOrder;
		this.jsonMapper = jsonMapper;

		if (logger.isDebugEnabled()) {
			logger.debug("Generated JSON Schema:\n" + outputJsonSchema);
		}

		JsonNode schemaNode;
		try {
			schemaNode = jsonMapper.readTree(outputJsonSchema);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse JSON schema", e);
		}

		SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
		this.jsonSchema = schemaRegistry.getSchema(schemaNode);

		this.maxRepeatAttempts = maxRepeatAttempts;
	}

	@SuppressWarnings("null")
	@Override
	public String getName() {
		return "Structured Output Validation Advisor";
	}

	@Override
	public int getOrder() {
		return this.advisorOrder;
	}

	@SuppressWarnings("null")
	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		Assert.notNull(callAdvisorChain, "callAdvisorChain must not be null");
		Assert.notNull(chatClientRequest, "chatClientRequest must not be null");

		ChatClientResponse chatClientResponse = null;

		boolean isValidationSuccess = false;

		var processedChatClientRequest = chatClientRequest;

		UsageAccumulator usageAccumulator = new UsageAccumulator();

		for (var currentAttemptNumber = 1 + this.maxRepeatAttempts; currentAttemptNumber > 0
				&& !isValidationSuccess; currentAttemptNumber--) {
			// Next Call
			chatClientResponse = callAdvisorChain.copy(this).nextCall(processedChatClientRequest);

			// After Call
			ChatResponse chatResponse = chatClientResponse.chatResponse();
			usageAccumulator.addRoundResponse(chatResponse);

			// We should not validate tool call requests, only the content of the final
			// response.
			if (chatResponse == null || !chatResponse.hasToolCalls()) {
				SchemaValidation validationResponse = validateOutputSchema(chatClientResponse,
						currentAttemptNumber - 1);

				isValidationSuccess = validationResponse.success();

				if (!isValidationSuccess) {

					// Add the validation error message to the next user message
					// to let the LLM fix its output.
					// Note: We could also consider adding the previous invalid output.
					// However, this might lead to confusion and more complex prompts.
					// Instead, we rely on the LLM to generate a new output based on the
					// validation error.
					if (logger.isWarnEnabled()) {
						logger.warn("JSON validation failed: " + validationResponse);
					}

					String validationErrorMessage = "Output JSON validation failed because of: "
							+ validationResponse.errorMessage();

					Prompt augmentedPrompt = chatClientRequest.prompt()
						.augmentUserMessage(userMessage -> userMessage.mutate()
							.text(userMessage.getText() + System.lineSeparator() + validationErrorMessage)
							.build());

					processedChatClientRequest = chatClientRequest.mutate().prompt(augmentedPrompt).build();
				}
				else if (logger.isDebugEnabled()) {
					logger.debug("JSON validation succeeded");
				}
			}
		}

		return usageAccumulator.applyAccumulatedUsage(Objects.requireNonNull(chatClientResponse));
	}

	private SchemaValidation validateOutputSchema(ChatClientResponse chatClientResponse, int leftAttemptsCounter) {

		if (chatClientResponse.chatResponse() == null || chatClientResponse.chatResponse().getResult() == null
				|| chatClientResponse.chatResponse().getResult().getOutput().getText() == null) {

			logger.warn("ChatClientResponse is missing required json output for validation.");
			return SchemaValidation.failed("Missing required json output for validation.");
		}

		// TODO: should we consider validation for multiple results?
		String json = chatClientResponse.chatResponse().getResult().getOutput().getText();

		if (logger.isDebugEnabled()) {
			logger.debug("Validating JSON output against schema. Attempts left: " + leftAttemptsCounter);
		}

		return validateJsonText(json);
	}

	private SchemaValidation validateJsonText(String json) {
		if (json.isBlank()) {
			return SchemaValidation.failed("Empty JSON output for validation.");
		}
		try {
			JsonNode instance = this.jsonMapper.readTree(json);
			List<Error> errors = this.jsonSchema.validate(instance);
			if (errors.isEmpty()) {
				return SchemaValidation.passed();
			}
			String message = errors.stream().map(Error::getMessage).collect(Collectors.joining("; "));
			return SchemaValidation.failed(message);
		}
		catch (JacksonException e) {
			return SchemaValidation.failed("Invalid JSON: " + e.getOriginalMessage());
		}
	}

	@SuppressWarnings("null")
	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {

		return Flux.error(new UnsupportedOperationException(
				"The Structured Output Validation Advisor does not support streaming."));
	}

	/**
	 * Returns a new {@link Builder}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link StructuredOutputValidationAdvisor}.
	 */
	public final static class Builder {

		private int advisorOrder = BaseAdvisor.LOWEST_PRECEDENCE - 2000;

		private @Nullable Type outputType;

		private int maxRepeatAttempts = 3;

		private JsonMapper jsonMapper = JacksonUtils.getDefaultJsonMapper();

		private @Nullable String outputJsonSchema;

		private Builder() {
		}

		/**
		 * Sets the advisor order. Must be strictly between
		 * {@link BaseAdvisor#HIGHEST_PRECEDENCE} and
		 * {@link BaseAdvisor#LOWEST_PRECEDENCE}. Defaults to
		 * {@code LOWEST_PRECEDENCE - 2000}.
		 * @param advisorOrder the advisor order
		 * @return this builder
		 */
		public Builder advisorOrder(int advisorOrder) {
			this.advisorOrder = advisorOrder;
			return this;
		}

		/**
		 * Sets the expected output type; the JSON schema is derived automatically.
		 * Mutually exclusive with {@link #outputJsonSchema(String)}.
		 * @param outputType the expected output type
		 * @return this builder
		 */
		public Builder outputType(Type outputType) {
			this.outputType = outputType;
			return this;
		}

		/**
		 * Sets the expected output type; the JSON schema is derived automatically.
		 * Mutually exclusive with {@link #outputJsonSchema(String)}.
		 * @param <T> the type parameter
		 * @param outputType the expected output type
		 * @return this builder
		 */
		public <T> Builder outputType(TypeReference<T> outputType) {
			this.outputType = outputType.getType();
			return this;
		}

		/**
		 * Sets the expected output type; the JSON schema is derived automatically.
		 * Mutually exclusive with {@link #outputJsonSchema(String)}.
		 * @param <T> the type parameter
		 * @param outputType the expected output type
		 * @return this builder
		 */
		public <T> Builder outputType(ParameterizedTypeReference<T> outputType) {
			this.outputType = outputType.getType();
			return this;
		}

		/**
		 * Sets a pre-generated JSON schema string to validate against. Mutually exclusive
		 * with the {@code outputType} methods.
		 * @param outputJsonSchema the JSON schema as a string
		 * @return this builder
		 */
		public Builder outputJsonSchema(String outputJsonSchema) {
			this.outputJsonSchema = outputJsonSchema;
			return this;
		}

		/**
		 * Sets the maximum number of retry attempts after a validation failure. Zero
		 * means no retries; the model is called exactly once. Defaults to 3.
		 * @param repeatAttempts the number of retry attempts, must be &gt;= 0
		 * @return this builder
		 */
		public Builder maxRepeatAttempts(int repeatAttempts) {
			this.maxRepeatAttempts = repeatAttempts;
			return this;
		}

		/**
		 * Sets the {@link JsonMapper} used for JSON parsing and validation. Defaults to
		 * {@link JacksonUtils#getDefaultJsonMapper()}.
		 * @param jsonMapper the JSON mapper
		 * @return this builder
		 */
		public Builder jsonMapper(JsonMapper jsonMapper) {
			this.jsonMapper = jsonMapper;
			return this;
		}

		/**
		 * Builds the StructuredOutputValidationAdvisor.
		 * @return a new StructuredOutputValidationAdvisor instance
		 * @throws IllegalArgumentException if neither outputType nor outputJsonSchema is
		 * set, or if both are set
		 */
		public StructuredOutputValidationAdvisor build() {

			if (StringUtils.hasText(this.outputJsonSchema) && this.outputType != null) {
				throw new IllegalArgumentException("Only outputType or outputJsonSchema can be set, not both.");
			}

			if (!StringUtils.hasText(this.outputJsonSchema) && this.outputType == null) {
				throw new IllegalArgumentException("Either outputType or outputJsonSchema must be set.");
			}

			if (this.outputType != null) {
				this.outputJsonSchema = JsonSchemaGenerator.generateForType(this.outputType);
			}

			return new StructuredOutputValidationAdvisor(this.advisorOrder,
					Objects.requireNonNull(this.outputJsonSchema), this.maxRepeatAttempts, this.jsonMapper);
		}

	}

	private record SchemaValidation(boolean success, String errorMessage) {

		private static SchemaValidation passed() {
			return new SchemaValidation(true, "");
		}

		private static SchemaValidation failed(String errorMessage) {
			return new SchemaValidation(false, errorMessage);
		}

	}

}
