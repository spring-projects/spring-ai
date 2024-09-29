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
package org.springframework.ai.chat.client.advisor.api;

import org.springframework.core.Ordered;

/**
 * Parent advisor interface for all advisors.
 *
 * @author Christian Tzolov
 * @author Dariusz Jedrzejczyk
 * @since 1.0.0
 * @see CallAroundAdvisor
 * @see StreamAroundAdvisor
 * @see CallAroundAdvisorChain
 */
public interface Advisor extends Ordered {

	/**
	 * Useful constant for the highest precedence value for ordering advisors.
	 */
	public static int HIGHEST_PRECEDENCE_ORDER = Ordered.HIGHEST_PRECEDENCE;

	/**
	 * Useful constant for the lowest precedence value for ordering advisors. Note that
	 * the values from Ordered.LOWEST_PRECEDENCE to Ordered.LOWEST_PRECEDENCE + 1000 are
	 * reserved for internal use within the Spring AI framework.
	 */
	public static int LOWEST_PRECEDENCE_ORDER = Ordered.LOWEST_PRECEDENCE + 1000;

	/**
	 * Useful constant for the default Chat Memory precedence order. Ensures this order
	 * has lower priority (e.g. precedences) than the Spring AI internal advisors. It
	 * leaves room (1000 slots) for the user to plug in their own advisors with higher
	 * priority.
	 */
	public static int DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER = Ordered.HIGHEST_PRECEDENCE + 1000;

	/**
	 * @return the advisor name.
	 */
	String getName();

}
