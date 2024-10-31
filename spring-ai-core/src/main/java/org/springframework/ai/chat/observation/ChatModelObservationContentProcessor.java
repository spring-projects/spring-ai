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

package org.springframework.ai.chat.observation;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.Content;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Utilities to process the prompt and completion content in observations for chat models.
 *
 * @author Thomas Vitale
 * @author John Blum
 * @since 1.0.0
 */
public abstract class ChatModelObservationContentProcessor {

	public static List<String> prompt(ChatModelObservationContext context) {

		List<Message> instructions = context.getRequest().getInstructions();

		return CollectionUtils.isEmpty(instructions) ? Collections.emptyList()
				: instructions.stream().map(Content::getContent).toList();
	}

	public static List<String> completion(@Nullable ChatModelObservationContext context) {

		return Optional.ofNullable(context)
			.map(ChatModelObservationContext::getResponse)
			.map(ChatResponse::getResults)
			.orElseGet(Collections::emptyList)
			.stream()
			.map(Generation::getOutput)
			.map(Message::getContent)
			.filter(StringUtils::hasText)
			.toList();
	}

}
