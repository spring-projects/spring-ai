/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link PromptTemplateUserTextProcessor}.
 *
 * @author Thomas Vitale
 */
class PromptTemplateUserTextProcessorTests {

	@ParameterizedTest
	@NullAndEmptySource
	void processWithNullOrEmptyUserText(String userText) {
		PromptTemplateUserTextProcessor processor = new PromptTemplateUserTextProcessor();
		Map<String, Object> userParams = Map.of("name", "William");
		assertThatIllegalArgumentException().isThrownBy(() -> processor.process(userText, userParams))
			.withMessage("userText cannot be null or empty");
	}

	@Test
	void processWithNullUserParams() {
		PromptTemplateUserTextProcessor processor = new PromptTemplateUserTextProcessor();
		String userText = "Hello, {name}!";
		Map<String, Object> userParams = null;
		assertThatIllegalArgumentException().isThrownBy(() -> processor.process(userText, userParams))
			.withMessage("userParams cannot be null");
	}

	@Test
	void processWithNullUserParamsKeys() {
		PromptTemplateUserTextProcessor processor = new PromptTemplateUserTextProcessor();
		String userText = "Hello, {name}!";
		Map<String, Object> userParams = new HashMap<>();
		userParams.put(null, "William");
		assertThatIllegalArgumentException().isThrownBy(() -> processor.process(userText, userParams))
			.withMessage("userParams keys cannot be null");
	}

	@Test
	void process() {
		PromptTemplateUserTextProcessor processor = new PromptTemplateUserTextProcessor();
		String userText = "Hello, {name}!";
		Map<String, Object> userParams = Map.of("name", "William");
		String processedText = processor.process(userText, userParams);
		assertThat(processedText).isEqualTo("Hello, William!");
	}

}
