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

package org.springframework.ai.google.genai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.core.retry.RetryTemplate;

/**
 * @author Mark Pollack
 */
public class TestGoogleGenAiGeminiChatModel extends GoogleGenAiChatModel {

	private GenerateContentResponse mockGenerateContentResponse;

	public TestGoogleGenAiGeminiChatModel(Client genAiClient, GoogleGenAiChatOptions options,
			RetryTemplate retryTemplate) {
		super(genAiClient, options, ToolCallingManager.builder().build(), retryTemplate, null);
	}

	@Override
	GenerateContentResponse getContentResponse(GeminiRequest request) {
		if (this.mockGenerateContentResponse != null) {
			return this.mockGenerateContentResponse;
		}
		return super.getContentResponse(request);
	}

	public void setMockGenerateContentResponse(GenerateContentResponse mockGenerateContentResponse) {
		this.mockGenerateContentResponse = mockGenerateContentResponse;
	}

}
