package org.springframework.ai.autoconfigure.zhipuai.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.autoconfigure.zhipuai.ZhipuAiAutoConfiguration;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.zhipuai.ZhipuAiChatClient;
import org.springframework.ai.zhipuai.ZhipuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".*")
class PaymentStatusBeanIT {

	private final Logger logger = LoggerFactory.getLogger(PaymentStatusBeanIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.zhipuai.apiKey=" + System.getenv("ZHIPU_AI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
				RestClientAutoConfiguration.class, ZhipuAiAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {

		contextRunner
			.withPropertyValues("spring.ai.zhipuai.chat.options.model=" + ZhipuAiApi.ChatModel.GLM_4.getValue())
			.run(context -> {

				var chatClient = context.getBean(ZhipuAiChatClient.class);

				var response = chatClient
					.call(new Prompt(List.of(new UserMessage("What's the status of my transaction with id T1001?")),
							ZhipuAiChatOptions.builder()
								.withFunction("retrievePaymentStatus")
								.withFunction("retrievePaymentDate")
								.build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getContent()).containsIgnoringCase("T1001");
				assertThat(response.getResult().getOutput().getContent()).containsIgnoringCase("paid");
			});
	}

	// Assuming we have the following data
	public static final Map<String, StatusDate> DATA = Map.of("T1001", new StatusDate("Paid", "2021-10-05"), "T1002",
			new StatusDate("Unpaid", "2021-10-06"), "T1003", new StatusDate("Paid", "2021-10-07"), "T1004",
			new StatusDate("Paid", "2021-10-05"), "T1005", new StatusDate("Pending", "2021-10-08"));

	record StatusDate(String status, String date) {
	}

	@Configuration
	static class Config {

		public record Transaction(@JsonProperty(required = true, value = "transaction_id") String transactionId) {
		}

		public record Status(@JsonProperty(required = true, value = "status") String status) {
		}

		public record Date(@JsonProperty(required = true, value = "date") String date) {
		}

		@Bean
		@Description("Get payment status of a transaction")
		public Function<Transaction, Status> retrievePaymentStatus() {
			return (transaction) -> new Status(DATA.get(transaction.transactionId).status());
		}

		@Bean
		@Description("Get payment date of a transaction")
		public Function<Transaction, Date> retrievePaymentDate() {
			return (transaction) -> new Date(DATA.get(transaction.transactionId).date());
		}

	}

}