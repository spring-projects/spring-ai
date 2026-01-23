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

package org.springframework.ai.vertexai.gemini;

import java.io.IOException;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;

import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.core.retry.RetryTemplate;

/**
 * @author Mark Pollack
 */
public class TestVertexAiGeminiChatModel extends VertexAiGeminiChatModel {

	private GenerativeModel mockGenerativeModel;

	public TestVertexAiGeminiChatModel(VertexAI vertexAI, VertexAiGeminiChatOptions options,
			RetryTemplate retryTemplate) {
		super(vertexAI, options, ToolCallingManager.builder().build(), retryTemplate, null);
	}

	@Override
	GenerateContentResponse getContentResponse(GeminiRequest request) {
		if (this.mockGenerativeModel != null) {
			try {
				return this.mockGenerativeModel.generateContent(request.contents());
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
