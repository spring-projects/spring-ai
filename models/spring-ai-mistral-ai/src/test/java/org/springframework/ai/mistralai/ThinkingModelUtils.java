/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mistralai;

import java.util.Set;

import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ReasoningEffort;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatModel;
import org.springframework.util.Assert;

/**
 * @author Nicolas Krier
 */
public interface ThinkingModelUtils {

	// @formatter:off
	Set<ChatModel> THINKING_MODELS = Set.of(
			ChatModel.MAGISTRAL_MEDIUM,
			ChatModel.MISTRAL_MEDIUM,
			ChatModel.MISTRAL_SMALL
	);
	// @formatter:on

	static String provideChatModelValue(ChatModel chatModel) {
		verifyChatModelIsReasoning(chatModel);

		// Mistral Medium Latest does not yet target model version 3.5, so this temporary
		// workaround is used.
		return ChatModel.MISTRAL_MEDIUM == chatModel ? "mistral-medium-3.5" : chatModel.getValue();
	}

	static ReasoningEffort provideReasoningEffort(ChatModel chatModel) {
		verifyChatModelIsReasoning(chatModel);

		// Reasoning effort is not supported by Magistral Medium model.
		return ChatModel.MAGISTRAL_MEDIUM == chatModel ? null : ReasoningEffort.HIGH;
	}

	private static void verifyChatModelIsReasoning(ChatModel chatModel) {
		Assert.isTrue(THINKING_MODELS.contains(chatModel), "Only reasoning model is supported!");
	}

}
