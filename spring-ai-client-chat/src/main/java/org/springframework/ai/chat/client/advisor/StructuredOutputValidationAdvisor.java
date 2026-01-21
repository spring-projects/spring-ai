/*
 * Copyright 2023-2025 the original author or authors.
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
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator.ValidationResponse;
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;

/**
 * Recursive Advisor that validates the structured JSON output of a chat client entity
 * response against a generated JSON schema for the expected output type.
 * <p>
 * If the validation fails, the advisor will repeat the call up to a specified number of
 * attempts.
 * <p>
 * Note: This advisor does not support streaming responses and will throw an
 * UnsupportedOperationException if used in a streaming context.
 *
 * @author Christian Tzolov
 */
public final class StructuredOutputValidationAdvisor implements CallAdvisor, StreamAdvisor {

	private static final Logger logger = LoggerFactory.getLogger(StructuredOutputValidationAdvisor.class);

	private static final TypeRef<HashMap<String, Object>> MAP_TYPE_REF = new TypeRef<>() {
	};

	/**
	 * Set the order close to {@link Ordered#LOWEST_PRECEDENCE} to ensure an advisor is
	 * executed toward the last (but before the model call) in the chain (last for request
	 * processing, first for response processing).
	 * <p>
	 * https://docs.spring.io/spring-ai/reference/api/advisors.html#_advisor_order
	 */
	private final int advisorOrder;

	/**
	 * The JSON schema used for validation.
	 */
	private final Map<String, Object> jsonSchema;

	/**
	 * The JSON schema validator.
	 */
	private final DefaultJsonSchemaValidator jsonvalidator;

	private final int maxRepeatAttempts;

	private StructuredOutputValidationAdvisor(int advisorOrder, Type outputType, int maxRepeatAttempts,
			ObjectMapper objectMapper) {
		Assert.notNull(advisorOrder, "advisorOrder must not be null");
		Assert.notNull(outputType, "outputType must not be null");
		Assert.isTrue(advisorOrder > BaseAdvisor.HIGHEST_PRECEDENCE && advisorOrder < BaseAdvisor.LOWEST_PRECEDENCE,
				"advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");
		Assert.isTrue(maxRepeatAttempts >= 0, "repeatAttempts must be greater than or equal to 0");
		Assert.notNull(objectMapper, "objectMapper must not be null");

		this.advisorOrder = advisorOrder;

		this.jsonvalidator = new DefaultJsonSchemaValidator(objectMapper);

		String jsonSchemaText = JsonSchemaGenerator.generateForType(outputType);

		logger.info("Generated JSON Schema:\n" + jsonSchemaText);

		var jsonMapper = new JacksonMcpJsonMapper(JsonParser.getObjectMapper());

		try {
			this.jsonSchema = jsonMapper.readValue(jsonSchemaText, MAP_TYPE_REF);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse JSON schema", e);
		}

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

		var repeatCounter = 0;

		boolean isValidationSuccess = true;

		var processedChatClientRequest = chatClientRequest;

		do {
			// Before Call
			repeatCounter++;

			// Next Call
			chatClientResponse = callAdvisorChain.copy(this).nextCall(processedChatClientRequest);

			// After Call

			// We should not validate tool call requests, only the content of the final
			// response.
			if (chatClientResponse.chatResponse() == null || !chatClientResponse.chatResponse().hasToolCalls()) {

				ValidationResponse validationResponse = this.validateOutputSchema(chatClientResponse);

				isValidationSuccess = validationResponse.valid();

				if (!isValidationSuccess) {

					// Add the validation error message to the next user message
					// to let the LLM fix its output.
					// Note: We could also consider adding the previous invalid output.
					// However, this might lead to confusion and more complex prompts.
					// Instead, we rely on the LLM to generate a new output based on the
					// validation error.
					logger.warn("JSON validation failed: " + validationResponse);

					String validationErrorMessage = "Output JSON validation failed because of: "
							+ validationResponse.errorMessage();

					Prompt augmentedPrompt = chatClientRequest.prompt()
						.augmentUserMessage(userMessage -> userMessage.mutate()
							.text(userMessage.getText() + System.lineSeparator() + validationErrorMessage)
							.build());

					processedChatClientRequest = chatClientRequest.mutate().prompt(augmentedPrompt).build();
				}
			}
		}
		while (!isValidationSuccess && repeatCounter <= this.maxRepeatAttempts);

		return chatClientResponse;
	}

	@SuppressWarnings("null")
	private ValidationResponse validateOutputSchema(ChatClientResponse chatClientResponse) {

		if (chatClientResponse.chatResponse() == null || chatClientResponse.chatResponse().getResult() == null
				|| chatClientResponse.chatResponse().getResult().getOutput() == null
				|| chatClientResponse.chatResponse().getResult().getOutput().getText() == null) {

			logger.warn("ChatClientResponse is missing required json output for validation.");
			return ValidationResponse.asInvalid("Missing required json output for validation.");
		}

		// TODO: should we consider validation for multiple results?
		String json = chatClientResponse.chatResponse().getResult().getOutput().getText();

		logger.debug("Validating JSON output against schema. Attempts left: " + this.maxRepeatAttempts);

		return this.jsonvalidator.validate(this.jsonSchema, json);
	}

	@SuppressWarnings("null")
	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {

		return Flux.error(new UnsupportedOperationException(
				"The Structured Output Validation Advisor does not support streaming."));
	}

	/**
	 * Creates a new Builder for StructuredOutputValidationAdvisor.
	 * @return a new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for StructuredOutputValidationAdvisor.
	 */
	public final static class Builder {

		/**
		 * Set the order close to {@link Ordered#LOWEST_PRECEDENCE} to ensure an advisor
		 * is executed toward the last (but before the model call) in the chain (last for
		 * request processing, first for response processing).
		 * <p>
		 * https://docs.spring.io/spring-ai/reference/api/advisors.html#_advisor_order
		 */
		private int advisorOrder = BaseAdvisor.LOWEST_PRECEDENCE - 2000;

		private @Nullable Type outputType;

		private int maxRepeatAttempts = 3;

		private ObjectMapper objectMapper = JsonParser.getObjectMapper();

		private Builder() {
		}

		/**
		 * Sets the advisor order.
		 * @param advisorOrder the advisor order
		 * @return this builder
		 */
		public Builder advisorOrder(int advisorOrder) {
			this.advisorOrder = advisorOrder;
			return this;
		}

		/**
		 * Sets the output type using a Type.
		 * @param outputType the output type
		 * @return this builder
		 */
		public Builder outputType(Type outputType) {
			this.outputType = outputType;
			return this;
		}

		/**
		 * Sets the output type using a TypeRef.
		 * @param <T> the type parameter
		 * @param outputType the output type
		 * @return this builder
		 */
		public <T> Builder outputType(TypeRef<T> outputType) {
			this.outputType = outputType.getType();
			return this;
		}

		/**
		 * Sets the output type using a TypeReference.
		 * @param <T> the type parameter
		 * @param outputType the output type
		 * @return this builder
		 */
		public <T> Builder outputType(TypeReference<T> outputType) {
			this.outputType = outputType.getType();
			return this;
		}

		/**
		 * Sets the output type using a ParameterizedTypeReference.
		 * @param <T> the type parameter
		 * @param outputType the output type
		 * @return this builder
		 */
		public <T> Builder outputType(ParameterizedTypeReference<T> outputType) {
			this.outputType = outputType.getType();
			return this;
		}

		/**
		 * Sets the number of repeat attempts.
		 * @param repeatAttempts the number of repeat attempts
		 * @return this builder
		 */
		public Builder maxRepeatAttempts(int repeatAttempts) {
			this.maxRepeatAttempts = repeatAttempts;
			return this;
		}

		/**
		 * Sets the ObjectMapper to be used for JSON processing.
		 * @param objectMapper the ObjectMapper
		 * @return this builder
		 */
		public Builder objectMapper(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;
			return this;
		}

		/**
		 * Builds the StructuredOutputValidationAdvisor.
		 * @return a new StructuredOutputValidationAdvisor instance
		 * @throws IllegalArgumentException if outputType is not set
		 */
		public StructuredOutputValidationAdvisor build() {
			if (this.outputType == null) {
				throw new IllegalArgumentException("outputType must be set");
			}
			return new StructuredOutputValidationAdvisor(this.advisorOrder, this.outputType, this.maxRepeatAttempts,
					this.objectMapper);
		}

	}

}
