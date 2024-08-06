/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.client;

import java.util.Map;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Advisor called before and after the {@link ChatModel#call(Prompt)} and
 * {@link ChatModel#stream(Prompt)} methods calls. The {@link ChatClient} maintains a
 * chain of advisors with chared execution context.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public interface RequestResponseAdvisor {

	/**
	 * @return the advisor name.
	 */
	default String getName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * @param request the {@link AdvisedRequest} data to be advised. Represents the row
	 * {@link ChatClient.ChatClientRequestSpec} data before sealed into a {@link Prompt}.
	 * @param context the shared data between the advisors in the chain. It is shared
	 * between all request and response advising points of all advisors in the chain.
	 * @return the advised {@link AdvisedRequest}.
	 */
	default AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
		return request;
	}

	/**
	 * @param response the {@link ChatResponse} data to be advised. Represents the row
	 * {@link ChatResponse} data after the {@link ChatModel#call(Prompt)} method is
	 * called.
	 * @param context the shared data between the advisors in the chain. It is shared
	 * between all request and response advising points of all advisors in the chain.
	 * @return the advised {@link ChatResponse}.
	 */
	default ChatResponse adviseResponse(ChatResponse response, Map<String, Object> context) {
		return response;
	}

	/**
	 * @param fluxResponse the streaming {@link ChatResponse} data to be advised.
	 * Represents the row {@link ChatResponse} stream data after the
	 * {@link ChatModel#stream(Prompt)} method is called.
	 * @param context the shared data between the advisors in the chain. It is shared
	 * between all request and response advising points of all advisors in the chain.
	 * @return the advised {@link ChatResponse} flux.
	 */
	default Flux<ChatResponse> adviseResponse(Flux<ChatResponse> fluxResponse, Map<String, Object> context) {
		return fluxResponse;
	}

}
