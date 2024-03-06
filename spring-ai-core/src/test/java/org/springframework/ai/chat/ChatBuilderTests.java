/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder;

/**
 * Unit Tests for {@link Prompt}.
 *
 * @author youngmon
 * @since 0.8.1
 */
public class ChatBuilderTests {

	@Test
	void createNewChatOptionsTest() {
		Float temperature = 1.1f;
		Float topP = 2.2f;
		Integer topK = 111;

		ChatOptions options = ChatOptionsBuilder.builder()
			.withTemperature(temperature)
			.withTopK(topK)
			.withTopP(topP)
			.build();

		assertThat(options.getTemperature()).isEqualTo(temperature);
		assertThat(options.getTopP()).isEqualTo(topP);
		assertThat(options.getTopK()).isEqualTo(topK);
	}

	@Test
	void duplicateChatOptionsTest() {
		Float initTemperature = 1.1f;
		Float initTopP = 2.2f;
		Integer initTopK = 111;

		ChatOptions options = ChatOptionsBuilder.builder()
			.withTemperature(initTemperature)
			.withTopP(initTopP)
			.withTopK(initTopK)
			.build();

	}

	@Test
	void createFunctionCallingOptionTest() {
		Float temperature = 1.1f;
		Float topP = 2.2f;
		Integer topK = 111;
		List<FunctionCallback> functionCallbacks = new ArrayList<>();
		Set<String> functions = new HashSet<>();

		String func = "func";
		FunctionCallback cb = FunctionCallbackWrapper.<Integer, Integer>builder(i -> i)
			.withName("cb")
			.withDescription("cb")
			.build();

		functions.add(func);
		functionCallbacks.add(cb);

		FunctionCallingOptions options = FunctionCallingOptions.builder()
			.withFunctionCallbacks(functionCallbacks)
			.withFunctions(functions)
			.withTopK(topK)
			.withTopP(topP)
			.withTemperature(temperature)
			.build();

		// Callback Functions
		assertThat(options.getFunctionCallbacks()).isNotNull();
		assertThat(options.getFunctionCallbacks().size()).isEqualTo(1);
		assertThat(options.getFunctionCallbacks().contains(cb));

		// Functions
		assertThat(options.getFunctions()).isNotNull();
		assertThat(options.getFunctions().size()).isEqualTo(1);
		assertThat(options.getFunctions().contains(func));

	}

}
