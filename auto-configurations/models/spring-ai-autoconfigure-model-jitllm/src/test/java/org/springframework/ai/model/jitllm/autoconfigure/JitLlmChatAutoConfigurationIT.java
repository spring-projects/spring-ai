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

package org.springframework.ai.model.jitllm.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.jitllm.JitLlmChatModel;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads a real GGUF model through the auto-configuration. Set {@code MODEL} to the model
 * file; the model runs on the GPU when the JVM was started through TornadoVM with
 * {@code -Duse.tornadovm=true}.
 *
 * @author Michalis Papadimitriou
 */
@EnabledIfEnvironmentVariable(named = "MODEL", matches = ".+")
class JitLlmChatAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class, JitLlmChatAutoConfiguration.class))
		.withPropertyValues("spring.ai.jitllm.chat.model-path=" + System.getenv("MODEL"),
				"spring.ai.jitllm.chat.on-gpu=" + Boolean.getBoolean("use.tornadovm"),
				"spring.ai.jitllm.chat.context-length=4096", "spring.ai.jitllm.chat.temperature=0.0",
				"spring.ai.jitllm.chat.max-tokens=512");

	@Test
	void theConfiguredModelAnswersAndCallsTools() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(JitLlmChatModel.class);
			ChatClient client = ChatClient.create(context.getBean(ChatModel.class));

			assertThat(client.prompt().user("What is the capital of France? Answer in one word.").call().content())
				.containsIgnoringCase("Paris");
			assertThat(client.prompt()
				.user("What is the weather in Munich right now? Use the tool.")
				.tools(new WeatherTools())
				.call()
				.content()).containsIgnoringCase("sunny");
		});
	}

	static class WeatherTools {

		@Tool(description = "Returns the current weather in a city")
		String getWeather(String city) {
			return "It is sunny and 21 degrees Celsius in " + city + ".";
		}

	}

}
