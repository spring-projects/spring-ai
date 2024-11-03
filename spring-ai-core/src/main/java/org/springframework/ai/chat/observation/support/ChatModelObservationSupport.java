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

package org.springframework.ai.chat.observation.support;

import java.util.Optional;
import java.util.function.Consumer;

import io.micrometer.observation.Observation;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.lang.Nullable;

/**
 * Support class for processing {@link ChatModel} Micrometer {@link Observation
 * Observations}. Intended mainly for internal use.
 *
 * @author John Blum
 * @see ChatModel
 * @see Observation
 * @since 1.0.0
 */
public abstract class ChatModelObservationSupport {

	public static Optional<ChatModelObservationContext> getObservationContext(@Nullable Observation observation) {

		// Avoid unnecessary construction of an Optional if Observations are not enabled
		// (aka NOOP).
		return Observation.NOOP.equals(observation) ? Optional.empty()
				: Optional.ofNullable(observation)
					.map(Observation::getContext)
					.filter(ChatModelObservationContext.class::isInstance)
					.map(ChatModelObservationContext.class::cast);
	}

	public static Consumer<ChatResponse> setChatResponseInObservationContext(@Nullable Observation observation) {
		return chatResponse -> getObservationContext(observation)
			.ifPresent(context -> context.setResponse(chatResponse));
	}

}
