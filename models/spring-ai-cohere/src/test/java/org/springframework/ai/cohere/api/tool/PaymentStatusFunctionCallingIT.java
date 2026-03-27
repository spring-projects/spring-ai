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

package org.springframework.ai.cohere.api.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletion;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.Role;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.cohere.api.CohereApi.FunctionTool;
import org.springframework.ai.cohere.api.CohereApi.FunctionTool.Type;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
public class PaymentStatusFunctionCallingIT {

	// Assuming we have the following data
	public static final Map<String, StatusDate> DATA = Map.of("T1001", new StatusDate("Paid", "2021-10-05"), "T1002",
			new StatusDate("Unpaid", "2021-10-06"), "T1003", new StatusDate("Paid", "2021-10-07"), "T1004",
			new StatusDate("Paid", "2021-10-05"), "T1005", new StatusDate("Pending", "2021-10-08"));

	static Map<String, Function<Transaction, ?>> functions = Map.of("retrieve_payment_status",
			new RetrievePaymentStatus(), "retrieve_payment_date", new RetrievePaymentDate());

	private final Logger logger = LoggerFactory.getLogger(PaymentStatusFunctionCallingIT.class);

	private static <T> T jsonToObject(String json, Class<T> targetClass) {
		try {
			return new ObjectMapper().readValue(json, targetClass);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	@SuppressWarnings("null")
	public void toolFunctionCall() throws JsonProcessingException {

		var transactionJsonSchema = """
				{
					"type": "object",
					"properties": {
						"transaction_id": {
							"type": "string",
							"description": "The transaction id"
						}
					},
					"required": ["transaction_id"]
				}
				""";

		var paymentStatusTool = new FunctionTool(Type.FUNCTION, new FunctionTool.Function(
				"Get payment status of a transaction", "retrieve_payment_status", transactionJsonSchema));

		var paymentDateTool = new FunctionTool(Type.FUNCTION, new FunctionTool.Function(
				"Get payment date of a transaction", "retrieve_payment_date", transactionJsonSchema));

		List<ChatCompletionMessage> messages = new ArrayList<>(
				List.of(new ChatCompletionMessage("What's the status of my transaction with id T1001?", Role.USER)));

		CohereApi cohereApi = CohereApi.builder().apiKey(System.getenv("COHERE_API_KEY")).build();

		ResponseEntity<ChatCompletion> response = cohereApi
			.chatCompletionEntity(new ChatCompletionRequest(messages, CohereApi.ChatModel.COMMAND_A_R7B.getValue(),
					List.of(paymentStatusTool, paymentDateTool), ToolChoice.REQUIRED));

		ChatCompletion chatCompletion = response.getBody();

		ChatCompletionMessage responseMessage = new ChatCompletionMessage(chatCompletion.message().content(),
				chatCompletion.message().role(), chatCompletion.message().toolPlan(),
				chatCompletion.message().toolCalls(), chatCompletion.message().citations(), null);

		assertThat(responseMessage.role()).isEqualTo(Role.ASSISTANT);
		assertThat(responseMessage.toolCalls()).isNotNull();

		// extend conversation with assistant's reply.
		messages.add(responseMessage);

		// Send the info for each function call and function response to the model.
		for (ToolCall toolCall : responseMessage.toolCalls()) {

			var functionName = toolCall.function().name();
			// Map the function, JSON arguments into a Transaction object.
			Transaction transaction = jsonToObject(toolCall.function().arguments(), Transaction.class);
			// Call the target function with the transaction object.
			var result = functions.get(functionName).apply(transaction);

			// Extend conversation with function response.
			// The functionName is used to identify the function response!
			messages.add(new ChatCompletionMessage(result.toString(), Role.TOOL, functionName, null,
					responseMessage.citations(), toolCall.id()));
		}

		response = cohereApi
			.chatCompletionEntity(new ChatCompletionRequest(messages, CohereApi.ChatModel.COMMAND_A_R7B.getValue()));

		chatCompletion = response.getBody();
		var content = chatCompletion.message().content().get(0).text();
		logger.info("Final response: {}", content);

		assertThat(content).containsIgnoringCase("T1001");
		assertThat(content).containsIgnoringCase("Paid");
	}

	record StatusDate(String status, String date) {

	}

	public record Transaction(@JsonProperty(required = true, value = "transaction_id") String transactionId) {

	}

	public record Status(@JsonProperty(required = true, value = "status") String status) {

	}

	public record Date(@JsonProperty(required = true, value = "date") String date) {

	}

	private static class RetrievePaymentStatus implements Function<Transaction, Status> {

		@Override
		public Status apply(Transaction paymentTransaction) {
			return new Status(DATA.get(paymentTransaction.transactionId).status);
		}

	}

	private static class RetrievePaymentDate implements Function<Transaction, Date> {

		@Override
		public Date apply(Transaction paymentTransaction) {
			return new Date(DATA.get(paymentTransaction.transactionId).date);
		}

	}

}
