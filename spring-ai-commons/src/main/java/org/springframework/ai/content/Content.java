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

package org.springframework.ai.content;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Data structure that contains content and metadata. Common parent for the
 * {@link org.springframework.ai.document.Document} and the
 * {@link org.springframework.ai.chat.messages.Message} classes.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0
 */
public interface Content {

	/**
	 * Get the content of the message.
	 * @return the content of the message
	 */
	@Nullable String getText();

	/**
	 * Get the metadata associated with the content.
	 * @return the metadata associated with the content
	 */
	Map<String, Object> getMetadata();

}
