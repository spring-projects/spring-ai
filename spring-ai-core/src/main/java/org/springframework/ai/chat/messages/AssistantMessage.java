/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.chat.messages;

import java.util.Map;

/**
 * Lets the generative know the content was generated as a response to the user. This role
 * indicates messages that the generative has previously generated in the conversation. By
 * including assistant messages in the series, you provide context to the generative about
 * prior exchanges in the conversation.
 */
public class AssistantMessage extends AbstractMessage {

	public AssistantMessage(String content) {
		super(MessageType.ASSISTANT, content);
	}

	public AssistantMessage(String content, Map<String, Object> properties) {
		super(MessageType.ASSISTANT, content, properties);
	}

	@Override
	public String toString() {
		return "AssistantMessage{" + "content='" + getContent() + '\'' + ", properties=" + properties + ", messageType="
				+ messageType + '}';
	}

}
