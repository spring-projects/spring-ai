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

package org.springframework.ai.converter;

import org.jspecify.annotations.Nullable;

/**
 * Strategy interface for cleaning LLM response text before parsing. Different
 * implementations can handle various response formats and patterns from different AI
 * models.
 *
 * @author liugddx
 * @since 1.1.0
 */
@FunctionalInterface
public interface ResponseTextCleaner {

	/**
	 * Clean the given text by removing unwanted patterns, tags, or formatting.
	 * @param text the raw text from LLM response
	 * @return the cleaned text ready for parsing
	 */
	@Nullable String clean(@Nullable String text);

}
