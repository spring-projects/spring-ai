/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.chatbot;

import org.springframework.ai.chat.prompt.transformer.PromptContext;

/**
 * A ChatBot encapsulates the logic to perform common AI use cases such as Retrieval
 * Augmented Generation.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0 M1
 */
public interface StreamingChatBot {

	/**
	 * Call the chatbot to execute AI actions
	 * @param promptContext A shared data structure used by the ChatBot to perform
	 * processing of the Prompt. It includes the intial Prompt and a conversation ID at
	 * the start of execution.
	 * @return the StreamingChatBotResponse that contains the ChatResponse and the latest
	 * PromptContext
	 */
	StreamingChatBotResponse stream(PromptContext promptContext);

}
