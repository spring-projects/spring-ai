package org.springframework.ai.autoconfigure.zhipuai.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.autoconfigure.zhipuai.ZhipuAiAutoConfiguration;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.zhipuai.ZhipuAiChatClient;
import org.springframework.ai.zhipuai.ZhipuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.ai.zhipuai.api.ZhipuAiApi.ChatCompletionRequest.ToolChoice;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".*")
public class WeatherServicePromptIT {

	private final Logger logger = LoggerFactory.getLogger(WeatherServicePromptIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.zhipuai.api-key=" + System.getenv("ZHIPU_AI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
				RestClientAutoConfiguration.class, ZhipuAiAutoConfiguration.class));

	@Test
	void promptFunctionCall() {
		contextRunner
			.withPropertyValues("spring.ai.zhipuai.chat.options.model=" + ZhipuAiApi.ChatModel.GLM_4.getValue())
			.run(context -> {

				var chatClient = context.getBean(ZhipuAiChatClient.class);

				UserMessage userMessage = new UserMessage(
						"What's the weather like in San Francisco, Tokyo, and Paris?");

				var promptOptions = ZhipuAiChatOptions.builder()
					.withToolChoice(ToolChoice.AUTO)
					.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MyWeatherService())
						.withName("CurrentWeatherService")
						.withDescription("Get the current weather in requested location")
						.build()))
					.build();

				ChatResponse response = chatClient.call(new Prompt(List.of(userMessage), promptOptions));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getContent()).contains("30.0", "10.0", "15.0");
			});
	}

	public static class MyWeatherService implements Function<MyWeatherService.Request, MyWeatherService.Response> {

		// @formatter:off
		public enum Unit { C, F }

		@JsonInclude(Include.NON_NULL)
		public record Request(
				@JsonProperty(required = true, value = "location") String location,
				@JsonProperty(required = true, value = "unit") Unit unit) {}

		public record Response(double temperature, Unit unit) {}
		// @formatter:on

		@Override
		public Response apply(Request request) {
			if (request.location().contains("Paris")) {
				return new Response(15, request.unit());
			}
			else if (request.location().contains("Tokyo")) {
				return new Response(10, request.unit());
			}
			else if (request.location().contains("San Francisco")) {
				return new Response(30, request.unit());
			}
			throw new IllegalArgumentException("Invalid request: " + request);
		}

	}

}