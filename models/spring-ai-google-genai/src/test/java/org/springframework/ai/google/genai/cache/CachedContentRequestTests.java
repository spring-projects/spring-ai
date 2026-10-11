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

import com.google.genai.types.Content;
import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CachedContentRequest}.
 *
 * @author Namju Kim
 */
class CachedContentRequestTests {

	@Test
	void addTextContentSetsUserRole() {
		CachedContentRequest request = CachedContentRequest.builder()
			.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_5_FLASH_LITE.getValue())
			.addTextContent("Large document")
			.ttl(Duration.ofMinutes(5))
			.build();

		assertThat(request.getContents()).hasSize(1);
		Content content = request.getContents().get(0);
		assertThat(content.role()).contains("user");
		assertThat(content.parts().get().get(0).text()).contains("Large document");
	}

}
