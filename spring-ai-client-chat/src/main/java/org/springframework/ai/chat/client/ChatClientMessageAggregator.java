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

package org.springframework.ai.chat.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.MessageAggregator;

/**
 * Helper that for streaming chat responses, aggregate the chat response messages into a
 * single AssistantMessage. Job is performed in parallel to the chat response processing.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ChatClientMessageAggregator {

	private static final Logger logger = LoggerFactory.getLogger(ChatClientMessageAggregator.class);

	@SuppressWarnings("NullAway") // https://github.com/uber/NullAway/issues/1350
	public Flux<ChatClientResponse> aggregateChatClientResponse(Flux<ChatClientResponse> chatClientResponses,
			Consumer<ChatClientResponse> aggregationHandler) {

		AtomicReference<Map<String, Object>> context = new AtomicReference<>(new HashMap<>());

		return new MessageAggregator().aggregate(chatClientResponses.mapNotNull(chatClientResponse -> {
			context.get().putAll(chatClientResponse.context());
			return chatClientResponse.chatResponse();
		}), aggregatedChatResponse -> {
			ChatClientResponse aggregatedChatClientResponse = ChatClientResponse.builder()
				.chatResponse(aggregatedChatResponse)
				.context(context.get())
				.build();
			aggregationHandler.accept(aggregatedChatClientResponse);
		}).map(chatResponse -> ChatClientResponse.builder().chatResponse(chatResponse).context(context.get()).build());
	}

}
