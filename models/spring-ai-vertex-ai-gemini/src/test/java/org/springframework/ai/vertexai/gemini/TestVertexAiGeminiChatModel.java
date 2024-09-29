package org.springframework.ai.vertexai.gemini;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.util.List;

public class TestVertexAiGeminiChatModel extends VertexAiGeminiChatModel {

	private GenerativeModel mockGenerativeModel;

	public TestVertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions options,
			FunctionCallbackContext functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks,
			RetryTemplate retryTemplate) {
		super(vertexAI, options, functionCallbackContext, toolFunctionCallbacks, retryTemplate);
	}

	@Override
	GenerateContentResponse getContentResponse(GeminiRequest request) {
		if (mockGenerativeModel != null) {
			try {
				return mockGenerativeModel.generateContent(request.contents());
			}
			catch (IOException e) {
				// Should not be thrown by testing class
				throw new RuntimeException("Failed to generate content", e);
			}
			catch (RuntimeException e) {
				// Re-throw RuntimeExceptions (including TransientAiException) as is
				throw e;
			}
		}
		return super.getContentResponse(request);
	}

	public void setMockGenerativeModel(GenerativeModel mockGenerativeModel) {
		this.mockGenerativeModel = mockGenerativeModel;
	}

}