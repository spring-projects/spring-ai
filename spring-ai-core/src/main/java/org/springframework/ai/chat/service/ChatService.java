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

package org.springframework.ai.chat.service;

import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;

/**
 * A ChatService encapsulates the logic to implement AI use cases.
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public interface ChatService {

	/**
	 * Call the service to execute AI actions
	 * @param chatServiceContext A data structure used by the ChatService to perform
	 * processing of the Prompt. It includes the initial Prompt and a conversation ID at
	 * the start of execution.
	 * @return the ChatServiceResponse that contains the ChatResponse and the latest
	 * ChatServiceContext
	 */
	ChatServiceResponse call(ChatServiceContext chatServiceContext);

}
