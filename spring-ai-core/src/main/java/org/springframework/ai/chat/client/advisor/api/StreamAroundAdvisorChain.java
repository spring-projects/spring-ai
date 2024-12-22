/*
 * Copyright 2023-2024 the original author or authors.
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

import reactor.core.publisher.Flux;

/**
 * The StreamAroundAdvisorChain is used to delegate the call to the next
 * StreamAroundAdvisor in the chain. Used for streaming responses.
 *
 * @author Christian Tzolov
 * @author Dariusz Jedrzejczyk
 * @since 1.0.0
 */
public interface StreamAroundAdvisorChain {

	/**
	 * This method delegates the call to the next StreamAroundAdvisor in the chain and is
	 * used for streaming responses.
	 * @param advisedRequest the request containing data of the chat client that can be
	 * modified before execution
	 * @return a Flux stream of AdvisedResponse objects
	 */
	Flux<AdvisedResponse> nextAroundStream(AdvisedRequest advisedRequest);

}
