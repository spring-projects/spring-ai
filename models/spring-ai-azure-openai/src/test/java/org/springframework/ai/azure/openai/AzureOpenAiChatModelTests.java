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

package org.springframework.ai.azure.openai;

import java.util.List;

import com.azure.ai.openai.OpenAIClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;

/**
 * @author Jihoon Kim
 */
@ExtendWith(MockitoExtension.class)
public class AzureOpenAiChatModelTests {

	@Mock
	OpenAIClientBuilder mockClient;

	@Mock
	FunctionCallbackContext functionCallbackContext;

	@Test
	public void createAzureOpenAiChatModelTest() {
		String callbackFromChatOptions = "callbackFromChatOptions";
		String callbackFromConstructorParam = "callbackFromConstructorParam";

		AzureOpenAiChatOptions chatOptions = AzureOpenAiChatOptions.builder()
			.withFunctionCallbacks(List.of(new TestFunctionCallback(callbackFromChatOptions)))
			.build();

		List<FunctionCallback> functionCallbacks = List.of(new TestFunctionCallback(callbackFromConstructorParam));

		AzureOpenAiChatModel openAiChatModel = new AzureOpenAiChatModel(this.mockClient, chatOptions,
				this.functionCallbackContext, functionCallbacks);

		assert 2 == openAiChatModel.getFunctionCallbackRegister().size();

		assert callbackFromChatOptions == openAiChatModel.getFunctionCallbackRegister()
			.get(callbackFromChatOptions)
			.getName();

		assert callbackFromConstructorParam == openAiChatModel.getFunctionCallbackRegister()
			.get(callbackFromConstructorParam)
			.getName();
	}

	private class TestFunctionCallback implements FunctionCallback {

		private final String name;

		TestFunctionCallback(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public String getDescription() {
			return null;
		}

		@Override
		public String getInputTypeSchema() {
			return null;
		}

		@Override
		public String call(String functionInput) {
			return null;
		}

	}

}
