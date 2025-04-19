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

package org.springframework.ai.chat.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * A policy for processing a message window in a chat memory system. It defines a strategy
 * for handling the addition of new messages to the existing message history, ensuring
 * that the total number of messages does not exceed a specified limit.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface MessageWindowProcessingPolicy {

	/**
	 * Processes the message window by adding new messages to the existing history
	 * messages and ensuring that the total number of messages does not exceed the
	 * specified limit.
	 */
	List<Message> process(List<Message> historyMessages, List<Message> newMessages, int limit);

}
