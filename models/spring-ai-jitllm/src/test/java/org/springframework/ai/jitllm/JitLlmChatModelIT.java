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

package org.springframework.ai.jitllm;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.beehive.jitllm.api.ThinkingMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Runs {@link JitLlmChatModel} against a real GGUF model. Set {@code MODEL} to the model
 * file. The model runs on the GPU when the JVM was started through TornadoVM with
 * {@code -Duse.tornadovm=true}, and on the CPU otherwise.
 *
 * @author Michalis Papadimitriou
 */
@EnabledIfEnvironmentVariable(named = "MODEL", matches = ".+")
class JitLlmChatModelIT {

	private static JitLlmChatModel chatModel;

	@BeforeAll
	static void loadModel() {
		chatModel = JitLlmChatModel.builder()
			.modelPath(Path.of(Objects.requireNonNull(System.getenv("MODEL"))))
			.onGpu(Boolean.getBoolean("use.tornadovm"))
			.contextLength(4096)
			.defaultOptions(JitLlmChatOptions.builder().temperature(0.0).maxTokens(512).seed(42L).build())
			.build();
	}

	@AfterAll
	static void closeModel() {
		chatModel.close();
	}

	@Test
	void answersAQuestion() {
		ChatResponse response = chatModel.call(new Prompt("What is the capital of France? Answer in one word."));

		assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("Paris");
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
	}

	@Test
	void streamsAnAnswer() {
		List<ChatResponse> chunks = chatModel.stream(new Prompt("Count from 1 to 5, separated by spaces."))
			.collectList()
			.block();

		assertThat(chunks).isNotNull().hasSizeGreaterThan(2);
		String text = chunks.stream()
			.map(chunk -> chunk.getResult().getOutput().getText())
			.filter(Objects::nonNull)
			.reduce("", String::concat);
		assertThat(text).contains("1").contains("5");
		assertThat(chunks.get(chunks.size() - 1).getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
	}

	@Test
	void callsAToolThroughChatClientAndUsesItsResult() {
		WeatherTools tools = new WeatherTools();

		String answer = ChatClient.create(chatModel)
			.prompt()
			.user("What is the weather in Munich right now? Use the tool.")
			.tools(tools)
			.call()
			.content();

		assertThat(tools.calls).hasValue(1);
		assertThat(answer).containsIgnoringCase("sunny");
	}

	@Test
	void aResponseCutOffByMaxTokensIsReturnedWithLength() {
		ChatResponse response = chatModel
			.call(new Prompt("Write a long story about a dragon.", JitLlmChatOptions.builder().maxTokens(8).build()));

		// A reasoning model may spend the whole budget thinking, so the truncated text
		// can
		// be in the thinking property rather than the content.
		AssistantMessage output = response.getResult().getOutput();
		assertThat(output.getText() + output.getMetadata().getOrDefault(JitLlmChatModel.THINKING_METADATA_KEY, ""))
			.isNotBlank();
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("LENGTH");
	}

	@Test
	void thinkingCanBeTurnedOff() {
		Assumptions.assumeTrue(Objects.requireNonNull(System.getenv("MODEL")).contains("Qwen3"),
				"needs a model with a reasoning phase");
		// On the CPU: closing a second GPU model in this JVM would reset the TornadoVM
		// state
		// the shared model uses. Thinking control is the chat template's, whatever the
		// backend.
		try (JitLlmChatModel noThinking = JitLlmChatModel.builder()
			.modelPath(Path.of(Objects.requireNonNull(System.getenv("MODEL"))))
			.onGpu(false)
			.contextLength(1024)
			.thinking(ThinkingMode.DISABLED)
			.defaultOptions(JitLlmChatOptions.builder().temperature(0.0).maxTokens(64).build())
			.build()) {
			ChatResponse response = noThinking.call(new Prompt("What is 2 + 2? Answer with the number."));

			assertThat(response.getResult().getOutput().getText()).contains("4");
			assertThat(String.valueOf(response.getResult()
				.getOutput()
				.getMetadata()
				.getOrDefault(JitLlmChatModel.THINKING_METADATA_KEY, ""))).isBlank();
			assertThat(response.getMetadata().getUsage().getCompletionTokens()).isLessThan(20);
		}
	}

	@Test
	void aModelThatDoesNotFitTheDeviceBudgetIsRefusedBeforeLoading() {
		Assumptions.assumeTrue(Boolean.getBoolean("use.tornadovm"), "needs a GPU");
		String budget = System.getProperty("tornado.device.memory");
		System.setProperty("tornado.device.memory", "64MB");
		try {
			assertThatIllegalStateException()
				.isThrownBy(() -> JitLlmChatModel.builder()
					.modelPath(Path.of(Objects.requireNonNull(System.getenv("MODEL"))))
					.onGpu(true)
					.build())
				.withMessageContaining("does not fit the device memory budget");
		}
		finally {
			if (budget != null) {
				System.setProperty("tornado.device.memory", budget);
			}
			else {
				System.clearProperty("tornado.device.memory");
			}
		}
	}

	static class WeatherTools {

		final AtomicInteger calls = new AtomicInteger();

		@Tool(description = "Returns the current weather in a city")
		String getWeather(@ToolParam(description = "The city") String city) {
			this.calls.incrementAndGet();
			return "It is sunny and 21 degrees Celsius in " + city + ".";
		}

	}

}
