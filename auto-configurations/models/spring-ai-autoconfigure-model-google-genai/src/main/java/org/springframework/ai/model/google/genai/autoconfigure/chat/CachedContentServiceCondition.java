/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.google.genai.autoconfigure.chat;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that checks if the GoogleGenAiCachedContentService can be created.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public class CachedContentServiceCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		try {
			// Check if GoogleGenAiChatModel bean exists
			if (!context.getBeanFactory().containsBean("googleGenAiChatModel")) {
				return ConditionOutcome.noMatch(ConditionMessage.forCondition("CachedContentService")
					.didNotFind("GoogleGenAiChatModel bean")
					.atAll());
			}

			// Get the chat model bean
			GoogleGenAiChatModel chatModel = context.getBeanFactory().getBean(GoogleGenAiChatModel.class);

			// Check if cached content service is available
			if (chatModel.getCachedContentService() == null) {
				return ConditionOutcome.noMatch(ConditionMessage.forCondition("CachedContentService")
					.because("chat model's cached content service is null"));
			}

			return ConditionOutcome
				.match(ConditionMessage.forCondition("CachedContentService").found("cached content service").atAll());
		}
		catch (Exception e) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition("CachedContentService")
				.because("error checking condition: " + e.getMessage()));
		}
	}

}
