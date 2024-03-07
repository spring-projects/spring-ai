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
package org.springframework.ai.model.function;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;

/**
 * Unit Tests for {@link FunctionCallingOptions}.
 *
 * @author youngmon
 * @since 0.8.1
 */
public class FunctionCallingOptionsTests {

	final String func = "func";

	final FunctionCallback cb = FunctionCallbackWrapper.<Integer, Integer>builder(i -> i)
		.withName("cb")
		.withDescription("cb")
		.build();

	@Test
	void createFunctionCallingOptionsTest() {
		FunctionCallingOptions options = FunctionCallingOptionsBuilder.builder().build();

		assertThat(options).isNotNull();
		assertThat(options).isInstanceOf(FunctionCallingOptions.class);
	}

	@Test
	void createPortableFunctionCallingOptionsTest() {
		PortableFunctionCallingOptions options = PortableFunctionCallingOptionsBuilder.builder().build();

		assertThat(options).isNotNull();
		assertThat(options).isInstanceOf(PortableFunctionCallingOptions.class);
	}

	@Test
	void castingPortableFunctionCallingOptionsTest() {
		ChatOptions chatOptions = PortableFunctionCallingOptionsBuilder.builder().build();
		FunctionCallingOptions functionCallingOptions = PortableFunctionCallingOptionsBuilder.builder().build();

		assertThat(chatOptions).isNotNull();
		assertThat(functionCallingOptions).isNotNull();

		assertThat(chatOptions).isInstanceOf(ChatOptions.class).isInstanceOf(PortableFunctionCallingOptions.class);
		assertThat(functionCallingOptions).isInstanceOf(FunctionCallingOptions.class)
			.isInstanceOf(PortableFunctionCallingOptions.class);
	}

	@Test
	void initFunctionCallingOptionsTest() {
		FunctionCallingOptions options = FunctionCallingOptionsBuilder.builder().build();

		// Callback Functions
		assertThat(options.getFunctionCallbacks()).isNotNull();
		assertThat(options.getFunctionCallbacks().isEmpty()).isTrue();

		// Functions
		assertThat(options.getFunctions()).isNotNull();
		assertThat(options.getFunctions().isEmpty()).isTrue();
	}

	@Test
	void initPortableFunctionCallingOptionsTest() {
		PortableFunctionCallingOptions options = PortableFunctionCallingOptionsBuilder.builder().build();

		// Callback Funcions
		assertThat(options.getFunctionCallbacks()).isNotNull();
		assertThat(options.getFunctionCallbacks().isEmpty()).isTrue();

		// Functions
		assertThat(options.getFunctions()).isNotNull();
		assertThat(options.getFunctions().isEmpty()).isTrue();
	}

	@Test
	void assignFunctionCallingOptionsTest() {
		FunctionCallingOptions options = FunctionCallingOptionsBuilder.builder()
			.withFunction(func)
			.withFunctionCallback(cb)
			.build();

		// Callback Functions
		assertThat(options.getFunctionCallbacks()).isNotNull();
		assertThat(options.getFunctions().size()).isEqualTo(1);
		assertThat(options.getFunctionCallbacks().contains(cb));

		// Functions
		assertThat(options.getFunctions()).isNotNull();
		assertThat(options.getFunctions().size()).isEqualTo(1);
		assertThat(options.getFunctions().contains(func));
	}

	@Test
	void assignPortableFunctionCallingOptionsTest() {
		PortableFunctionCallingOptions options = PortableFunctionCallingOptionsBuilder.builder()
			.withFunction(func)
			.withFunctionCallback(cb)
			.build();

		// Callback Functions
		assertThat(options.getFunctionCallbacks()).isNotNull();
		assertThat(options.getFunctions().size()).isEqualTo(1);
		assertThat(options.getFunctionCallbacks().contains(cb));

		// Functions
		assertThat(options.getFunctions()).isNotNull();
		assertThat(options.getFunctions().size()).isEqualTo(1);
		assertThat(options.getFunctions().contains(func));
	}

	@Test
	void convertPortableFunctionCallingOptionsTest() {
		String func = "func";
		Integer topK = 123;

		PortableFunctionCallingOptions options = PortableFunctionCallingOptionsBuilder.builder()
			.withFunction(func)
			.withTopK(topK)
			.build();

		assertThat(options.getFunctions()).contains(func);
		assertThat(options.getTopK()).isEqualTo(topK);

		// type
		assertThat(options).isInstanceOf(ChatOptions.class);
		assertThat(options).isInstanceOf(FunctionCallingOptions.class);

		// up casting
		FunctionCallingOptions functionCallingOptions = options;
		ChatOptions chatOptions = options;

		assertThat(functionCallingOptions.getFunctions()).contains(func);
		assertThat(chatOptions.getTopK()).isEqualTo(topK);

		// down casting
		{
			PortableFunctionCallingOptions tmp = (PortableFunctionCallingOptions) functionCallingOptions;
			assertThat(tmp.getFunctions()).contains(func);
		}
		{
			PortableFunctionCallingOptions tmp = (PortableFunctionCallingOptions) chatOptions;
			assertThat(tmp.getTopK()).isEqualTo(topK);
		}

		// build new Instance for modify
		String newFunc = "newFunc";
		Float topP = 1.2f;

		FunctionCallingOptions newFunctionCallingOptions = FunctionCallingOptionsBuilder.builder(options)
			.withFunction(newFunc)
			.build();
		ChatOptions newChatOptions = ChatOptionsBuilder.builder(options).withTopP(topP).build();

		assertThat(newFunctionCallingOptions.getFunctions()).contains(func, newFunc);
		assertThat(newChatOptions.getTopK()).isEqualTo(topK);
		assertThat(newChatOptions.getTopP()).isEqualTo(topP);
	}

}
