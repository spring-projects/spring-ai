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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * A policy that adds new messages to the existing history messages and ensures that the
 * total number of messages does not exceed the specified limit.
 * <p>
 * Messages of type {@link SystemMessage} are treated specially: if a new
 * {@link SystemMessage} is added, all previous {@link SystemMessage} instances are
 * removed from the history. Also, if the total number of messages exceeds the limit, the
 * {@link SystemMessage} messages are preserved while removing other types of messages.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class DefaultMessageWindowProcessingPolicy implements MessageWindowProcessingPolicy {

	@Override
	public List<Message> process(List<Message> historyMessages, List<Message> newMessages, int limit) {
		Assert.notNull(historyMessages, "historyMessages cannot be null");
		Assert.noNullElements(historyMessages, "historyMessages cannot contain null elements");
		Assert.notNull(newMessages, "newMessages cannot be null");
		Assert.noNullElements(newMessages, "newMessages cannot contain null elements");
		Assert.isTrue(limit > 0, "limit must be greater than 0");

		List<Message> processedMessages = new ArrayList<>(historyMessages);

		for (Message newMessage : newMessages) {
			if (newMessage instanceof SystemMessage systemMessage && !processedMessages.contains(systemMessage)) {
				// If a new SystemMessage is added, remove all previous SystemMessages
				processedMessages.removeIf(m -> m instanceof SystemMessage);
				break;
			}
		}

		processedMessages.addAll(new ArrayList<>(newMessages));

		if (processedMessages.size() <= limit) {
			return processedMessages;
		}

		int messagesToRemove = processedMessages.size() - limit;
		int index = 0;

		while (messagesToRemove > 0 && index < processedMessages.size()) {
			if (!(processedMessages.get(index) instanceof SystemMessage)) {
				processedMessages.remove(index);
				messagesToRemove--;
			}
			else {
				index++;
			}
		}

		return processedMessages;
	}

}
