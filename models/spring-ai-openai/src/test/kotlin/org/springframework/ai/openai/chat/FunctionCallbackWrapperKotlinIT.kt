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

package org.springframework.ai.openai.chat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.ai.model.function.FunctionCallbackWrapper
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class FunctionCallbackWrapperKotlinIT {

	private val logger = LoggerFactory.getLogger(FunctionCallbackWrapperKotlinIT::class.java)


	private val contextRunner = ApplicationContextRunner()
		.withUserConfiguration(Config::class.java)


	@Test
	fun functionCallTest() {
		this.contextRunner.run {context ->

			val chatModel = context.getBean(OpenAiChatModel::class.java)
			assertThat(chatModel).isNotNull

			val userMessage = UserMessage(
				"What are the weather conditions in San Francisco, Tokyo, and Paris? Find the temperature in Celsius for each of the three locations.")

			val response = chatModel
				.call(Prompt(listOf(userMessage), OpenAiChatOptions.builder().withFunction("WeatherInfo").build()))

			logger.info("Response: " + response)

			assertThat(response.getResult().output.content).contains("30", "10", "15")
		}
	}


	@SpringBootConfiguration
	open class Config {

		@Bean
		open fun chatCompletionApi(): OpenAiApi {
			return OpenAiApi(System.getenv("OPENAI_API_KEY"))
		}

		@Bean
		open fun openAiClient(openAiApi: OpenAiApi): OpenAiChatModel {
			return OpenAiChatModel(openAiApi)
		}

		@Bean
		open fun weatherFunctionInfo(): FunctionCallback {

			return FunctionCallbackWrapper.builder(MockKotlinWeatherService())
				.withName("WeatherInfo")
				.withInputType(KotlinRequest::class.java)
				.withDescription(
					"Find the weather conditions, forecasts, and temperatures for a location, like a city or state.")
				.build();
		}
	}
}
