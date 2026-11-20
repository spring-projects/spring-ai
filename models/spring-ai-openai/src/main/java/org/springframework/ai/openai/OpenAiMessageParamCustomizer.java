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

package org.springframework.ai.openai;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import org.springframework.ai.chat.messages.Message;

/**
 * Callback interface that can be used to customize a {@link ChatCompletionMessageParam}
 * created from a Spring AI {@link Message} before it is sent to the OpenAI Chat
 * Completions API.
 *
 * @see OpenAiChatModel.Builder#messageParamCustomizer(OpenAiMessageParamCustomizer)
 * @since 2.1.0
 */
@FunctionalInterface
public interface OpenAiMessageParamCustomizer {

	/**
	 * Customize the converted {@link ChatCompletionMessageParam} for the given source
	 * {@link Message}.
	 * @param message the original Spring AI message
	 * @param messageParam the default OpenAI SDK message parameter built by Spring AI
	 * @return the customized {@link ChatCompletionMessageParam}
	 */
	ChatCompletionMessageParam customize(Message message, ChatCompletionMessageParam messageParam);

}
