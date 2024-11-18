package org.springframework.ai.azure.openai;

import com.azure.ai.openai.OpenAIClientBuilder;

import java.util.List;

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

		AzureOpenAiChatModel openAiChatModel = new AzureOpenAiChatModel(mockClient, chatOptions,
				functionCallbackContext, functionCallbacks);

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

		public TestFunctionCallback(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
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