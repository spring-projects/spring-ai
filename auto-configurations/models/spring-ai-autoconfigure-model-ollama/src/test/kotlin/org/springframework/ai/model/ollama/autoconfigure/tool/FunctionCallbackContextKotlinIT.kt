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

package org.springframework.ai.model.ollama.autoconfigure.tool

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.ollama.autoconfigure.BaseOllamaIT
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description

class FunctionCallbackResolverKotlinIT : BaseOllamaIT() {

	companion object {

		private val MODEL_NAME = "qwen2.5:3b";

		@JvmStatic
		@BeforeAll
		fun beforeAll() {
			initializeOllama(MODEL_NAME)
		}
	}

	private val logger = LoggerFactory.getLogger(FunctionCallbackResolverKotlinIT::class.java)

	private val contextRunner = ApplicationContextRunner()
		.withPropertyValues(
			"spring.ai.ollama.baseUrl=${getBaseUrl()}",
			"spring.ai.ollama.chat.options.model=$MODEL_NAME",
			"spring.ai.ollama.chat.options.temperature=0.5",
			"spring.ai.ollama.chat.options.topK=10"
		)
		.withConfiguration(ollamaAutoConfig(OllamaChatAutoConfiguration::class.java))
		.withUserConfiguration(Config::class.java)

	@Test
	fun toolCallTest() {
		this.contextRunner.run {context ->

			val chatModel = context.getBean(OllamaChatModel::class.java)

			val userMessage = UserMessage(
				"What are the weather conditions in San Francisco, Tokyo, and Paris? Find the temperature in Celsius for each of the three locations.")

			val response = chatModel
					.call(Prompt(listOf(userMessage), OllamaChatOptions.builder().toolNames("weatherInfo").build()))

			logger.info("Response: $response")

			assertThat(response.getResult()!!.output.text).contains("30", "10", "15")
		}
	}

	@Test
	fun functionCallWithPortableFunctionCallingOptions() {
		this.contextRunner.run { context ->

			val chatModel = context.getBean(OllamaChatModel::class.java)

			// Test weatherFunction
			val userMessage = UserMessage(
				"What are the weather conditions in San Francisco, Tokyo, and Paris? Find the temperature in Celsius for each of the three locations.")

			val functionOptions = ToolCallingChatOptions.builder()
				.toolNames("weatherInfo")
				.build()

			val response = chatModel.call(Prompt(listOf(userMessage), functionOptions));
			val output = response.getResult()!!.output.text

			logger.info("Response: $output");

			assertThat(output).contains("30", "10", "15");
		}
	}

	@Configuration
	open class Config {

		@Bean
		@Description("Find the weather conditions, forecasts, and temperatures for a location, like a city or state.")
		open fun weatherInfo(): (KotlinRequest) -> KotlinResponse = { request ->
			val temperature = when {
				request.location.contains("Paris") -> 15.0
				request.location.contains("Tokyo") -> 10.0
				request.location.contains("San Francisco") -> 30.0
				else -> 10.0
			}
			KotlinResponse(temperature, 15.0, 20.0, 2.0, 53, 45, Unit.C)
		}
	}
}
