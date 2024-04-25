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

package org.springframework.ai.chat.agent;

import org.springframework.ai.chat.prompt.transformer.PromptContext;

/**
 * A ChatAgent encapsulates common AI workflows such as Retrieval Augmented Generation.
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public interface ChatAgent {

	/**
	 * Call the chat agent to execute a workflow
	 * @param promptContext A shared data structure that can be used in components that
	 * implement the workflow. Contains the initial Prompt and a conversation ID at the
	 * start of the workflow.
	 * @return the AgentResponse that contains the ChatResponse and the latest
	 * PromptContext
	 */
	AgentResponse call(PromptContext promptContext);

}
