/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.openai.chat.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import reactor.core.publisher.Flux;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@ActiveProfiles("logging-test")
class OpenAiChatClientMultipleFunctionCallsIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatClientMultipleFunctionCallsIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

	record ActorsFilms(String actor, List<String> movies) {
	}

	@Test
	void turnFunctionsOnAndOffTest() {

		var chatClientBuilder = ChatClient.builder(chatModel);

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
				.function("getCurrentWeather", "Get the weather in location", new MockWeatherService())
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
		String response = ChatClient.builder(chatModel)
				.defaultFunction("getCurrentWeather", "Get the weather in location", new MockWeatherService())
				.defaultUser(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
			.build()
			.prompt().call().content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallTest() {

		// @formatter:off
		Flux<String> response = ChatClient.create(chatModel).prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris?")
				.function("getCurrentWeather", "Get the weather in location", new MockWeatherService())
				.stream()
				.content();
		// @formatter:on

		String content = response.collectList().block().stream().collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).contains("30", "10", "15");

	}

	@Test
	void functionCallWithExplicitInputType() throws NoSuchMethodException {

		var chatClient = ChatClient.create(chatModel);

		Method currentTemp = MyFunction.class.getMethod("getCurrentTemp", MyFunction.Req.class);

		// NOTE: Lambda functions do not retain the type information, so we need to
		// provide the input type explicitly.
		MyFunction myFunction = new MyFunction();
		Function<MyFunction.Req, Object> function = createFunction(myFunction, currentTemp);

		ChatClient.ChatClientRequestSpec chatClientRequestSpec = chatClient.prompt()
			.user("What's the weather like in Shanghai?")
			.function("currentTemp", "get current temp", MyFunction.Req.class, function);

		String content = chatClientRequestSpec.call().content();

		assertThat(content).contains("23");
	}

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

	public static class MyFunction {

		public record Req(String city) {
		}

		public String getCurrentTemp(Req req) {
			return "23";
		}

	}

}