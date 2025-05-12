/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.openai.chat.client;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.openai.api.tool.MockWeatherService.Request;
import org.springframework.ai.openai.api.tool.MockWeatherService.Response;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@ActiveProfiles("logging-test")
class OpenAiChatClientMultipleFunctionCallsIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatClientMultipleFunctionCallsIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

	public static <T, R> Function<T, R> createFunction(Object obj, Method method) {
		return (T t) -> {
			try {
				return (R) method.invoke(obj, t);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
	}

	@Test
	void turnFunctionsOnAndOffTest() {

		var chatClientBuilder = ChatClient.builder(this.chatModel);

		// @formatter:off
		String response = chatClientBuilder.build().prompt()
				.user(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).doesNotContain("30", "10", "15");

		// @formatter:off
		response = chatClientBuilder.build().prompt()
				.user(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");

		// @formatter:off
		response = chatClientBuilder.build().prompt()
				.user(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).doesNotContain("30", "10", "15");

	}

	@Test
	void defaultFunctionCallTest() {

		// @formatter:off
		String response = ChatClient.builder(this.chatModel)
				.defaultToolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.defaultUser(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
			.build()
			.prompt().call().content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@Test
	void defaultFunctionCallTestWithToolContext() {

		var biFunction = new BiFunction<MockWeatherService.Request, ToolContext, MockWeatherService.Response>() {

			@Override
			public Response apply(Request request, ToolContext toolContext) {

				assertThat(toolContext.getContext()).containsEntry("sessionId", "123");

				double temperature = 0;
				if (request.location().contains("Paris")) {
					temperature = 15;
				}
				else if (request.location().contains("Tokyo")) {
					temperature = 10;
				}
				else if (request.location().contains("San Francisco")) {
					temperature = 30;
				}

				return new MockWeatherService.Response(temperature, 15, 20, 2, 53, 45, MockWeatherService.Unit.C);
			}

		};

		// @formatter:off
		String response = ChatClient.builder(this.chatModel)
				.defaultToolCallbacks(FunctionToolCallback.builder("getCurrentWeather", biFunction)
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.defaultUser(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
				.defaultToolContext(Map.of("sessionId", "123"))
			.build()
			.prompt().call().content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@Test
	void functionCallTestWithToolContext() {

		var biFunction = new BiFunction<MockWeatherService.Request, ToolContext, MockWeatherService.Response>() {

			@Override
			public Response apply(Request request, ToolContext toolContext) {

				assertThat(toolContext.getContext()).containsEntry("sessionId", "123");

				double temperature = 0;
				if (request.location().contains("Paris")) {
					temperature = 15;
				}
				else if (request.location().contains("Tokyo")) {
					temperature = 10;
				}
				else if (request.location().contains("San Francisco")) {
					temperature = 30;
				}

				return new MockWeatherService.Response(temperature, 15, 20, 2, 53, 45, MockWeatherService.Unit.C);
			}

		};

		// @formatter:off
		String response = ChatClient.builder(this.chatModel)
				.defaultToolCallbacks(FunctionToolCallback.builder("getCurrentWeather", biFunction)
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.defaultUser(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
				.build()
			.prompt()
			.toolContext(Map.of("sessionId", "123"))
			.call().content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallTest() {

		// @formatter:off
		Flux<String> response = ChatClient.create(this.chatModel).prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris?")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build())
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).contains("30", "10", "15");

	}

	@Test
	void functionCallWithExplicitInputType() throws NoSuchMethodException {

		var chatClient = ChatClient.create(this.chatModel);

		Method currentTemp = MyFunction.class.getMethod("getCurrentTemp", MyFunction.Req.class);

		// NOTE: Lambda functions do not retain the type information, so we need to
		// provide the input type explicitly.
		MyFunction myFunction = new MyFunction();
		Function<MyFunction.Req, Object> function = createFunction(myFunction, currentTemp);

		String content = chatClient.prompt()
			.user("What's the weather like in Shanghai?")
			.toolCallbacks(FunctionToolCallback.builder("currentTemp", function)
				.description("get current temp")
				.inputType(MyFunction.Req.class)
				.build())
			.call()
			.content();

		assertThat(content).contains("23");
	}

	record ActorsFilms(String actor, List<String> movies) {

	}

	public static class MyFunction {

		public String getCurrentTemp(Req req) {
			return "23";
		}

		public record Req(String city) {

		}

	}

}
