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

package org.springframework.ai.google.genai.cache;

import java.time.Duration;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link GoogleGenAiCachedContentService} against the Gemini
 * Developer API. These tests require a GOOGLE_API_KEY environment variable.
 *
 * @author Namju Kim
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GoogleGenAiCachedContentServiceIT {

	private final GoogleGenAiCachedContentService service = new GoogleGenAiCachedContentService(
			Client.builder().apiKey(System.getenv("GOOGLE_API_KEY")).build());

	@Test
	void createCachedContentFromTextContent() {
		CachedContentRequest request = CachedContentRequest.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_5_FLASH_LITE.getValue())
			.displayName("spring-ai-text-content-it")
			.addTextContent(largeDocument())
			.ttl(Duration.ofMinutes(5))
			.build();

		GoogleGenAiCachedContent cachedContent = this.service.create(request);

		try {
			assertThat(cachedContent).isNotNull();
			assertThat(cachedContent.getName()).startsWith("cachedContents/");
			assertThat(cachedContent.getUsageMetadata()).isNotNull();
		}
		finally {
			if (cachedContent != null) {
				this.service.delete(cachedContent.getName());
			}
		}
	}

	/**
	 * Builds a document that is comfortably above the minimum token count required for
	 * explicit context caching.
	 */
	private static String largeDocument() {
		StringBuilder document = new StringBuilder();
		for (int i = 1; i <= 400; i++) {
			document.append("Section ")
				.append(i)
				.append(": Spring AI provides portable abstractions for chat, embedding and image models. ");
		}
		return document.toString();
	}

}
