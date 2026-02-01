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

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;

/**
 * A chain of {@link StreamAdvisor} instances orchestrating the execution of a
 * {@link ChatClientRequest} on the next {@link StreamAdvisor} in the chain.
 *
 * @author Christian Tzolov
 * @author Dariusz Jedrzejczyk
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface StreamAdvisorChain extends AdvisorChain {

	/**
	 * Invokes the next {@link StreamAdvisor} in the {@link StreamAdvisorChain} with the
	 * given request.
	 */
	Flux<ChatClientResponse> nextStream(ChatClientRequest chatClientRequest);

	/**
	 * Returns the list of all the {@link StreamAdvisor} instances included in this chain
	 * at the time of its creation.
	 */
	List<StreamAdvisor> getStreamAdvisors();

	/**
	 * Creates a new StreamAdvisorChain copy that contains all advisors after the
	 * specified advisor.
	 * @param after the StreamAdvisor after which to copy the chain
	 * @return a new StreamAdvisorChain containing all advisors after the specified
	 * advisor
	 * @throws IllegalArgumentException if the specified advisor is not part of the chain
	 */
	StreamAdvisorChain copy(StreamAdvisor after);

}
