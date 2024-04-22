package org.springframework.ai.zhipuai.api.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.ChatCompletion;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.ChatCompletionMessage;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.ChatCompletionRequest;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.FunctionTool;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.FunctionTool.Type;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates how to use function calling suing Zhipu AI Java API: {@link ZhipuAiApi}.
 *
 * @author Ricken Bazolo
 */
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".+")
public class PaymentStatusFunctionCallingIT {

	private final Logger logger = LoggerFactory.getLogger(PaymentStatusFunctionCallingIT.class);

	// Assuming we have the following data
	public static final Map<String, StatusDate> DATA = Map.of("T1001", new StatusDate("Paid", "2021-10-05"), "T1002",
			new StatusDate("Unpaid", "2021-10-06"), "T1003", new StatusDate("Paid", "2021-10-07"), "T1004",
			new StatusDate("Paid", "2021-10-05"), "T1005", new StatusDate("Pending", "2021-10-08"));

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

	static Map<String, Function<Transaction, ?>> functions = Map.of("retrieve_payment_status",
			new RetrievePaymentStatus(), "retrieve_payment_date", new RetrievePaymentDate());

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

		var mistralApi = new ZhipuAiApi(System.getenv("ZHIPU_AI_API_KEY"));

		ResponseEntity<ChatCompletion> response = mistralApi.chatCompletionEntity(new ChatCompletionRequest(messages,
				ZhipuAiApi.ChatModel.GLM_4.getValue(), List.of(paymentStatusTool, paymentDateTool), ToolChoice.AUTO));

		ChatCompletionMessage responseMessage = response.getBody().choices().get(0).message();

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
			messages.add(new ChatCompletionMessage(result.toString(), Role.TOOL, functionName, null));
		}

		response = mistralApi
			.chatCompletionEntity(new ChatCompletionRequest(messages, ZhipuAiApi.ChatModel.GLM_4.getValue()));

		var responseContent = response.getBody().choices().get(0).message().content();
		logger.info("Final response: " + responseContent);

		assertThat(responseContent).containsIgnoringCase("T1001");
		assertThat(responseContent).containsIgnoringCase("Paid");
	}

	private static <T> T jsonToObject(String json, Class<T> targetClass) {
		try {
			return new ObjectMapper().readValue(json, targetClass);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

}
