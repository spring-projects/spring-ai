/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;

/**
 * A chain of {@link CallAdvisor} instances orchestrating the execution of a
 * {@link ChatClientRequest} on the next {@link CallAdvisor} in the chain.
 *
 * @author Christian Tzolov
 * @author Dariusz Jedrzejczyk
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface CallAdvisorChain extends AdvisorChain {

	/**
	 * Invokes the next {@link CallAdvisor} in the {@link CallAdvisorChain} with the given
	 * request.
	 */
	ChatClientResponse nextCall(ChatClientRequest chatClientRequest);

	/**
	 * Returns the list of all the {@link CallAdvisor} instances included in this chain at
	 * the time of its creation.
	 */
	List<CallAdvisor> getCallAdvisors();

}
