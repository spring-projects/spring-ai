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

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * Processes the advised user text with the given user parameters using a
 * {@link PromptTemplate}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class PromptTemplateUserTextProcessor implements UserTextProcessor {

	@Override
	public String process(String userText, Map<String, Object> userParams) {
		Assert.hasText(userText, "userText cannot be null or empty");
		Assert.notNull(userParams, "userParams cannot be null");
		Assert.noNullElements(userParams.keySet(), "userParams keys cannot be null");

		return new PromptTemplate(userText, userParams).render();
	}

}
