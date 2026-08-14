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

package org.springframework.ai.chat.client.advisor.api;

import org.springframework.core.Ordered;

/**
 * Parent advisor interface for all advisors.
 *
 * @author Christian Tzolov
 * @author Dariusz Jedrzejczyk
 * @since 1.0.0
 * @see CallAdvisor
 * @see StreamAdvisor
 * @see BaseAdvisor
 */
public interface Advisor extends Ordered {

	/**
	 * Default order for chat-memory advisors. Placed before (outside)
	 * {@link org.springframework.ai.chat.client.advisor.ToolCallingAdvisor#DEFAULT_ORDER}
	 * so the memory advisor wraps the tool-call loop, and the {@code ToolCallingAdvisor}
	 * manages its own intermediate conversation history.
	 */
	int DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER = Ordered.HIGHEST_PRECEDENCE + 200;

	/**
	 * Return the name of the advisor.
	 * @return the advisor name.
	 */
	String getName();

}
