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

package org.springframework.ai.chat.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Advisor called before and after the {@link ChatModel#call(Prompt)} and
 * {@link ChatModel#stream(Prompt)} methods calls. The {@link ChatClient} maintains a
 * chain of advisors with shared advise context.
 *
 * @deprecated since 1.0.0 M3 please use {@link CallAroundAdvisor} or
 * {@link StreamAroundAdvisor} instead.
 * @author Christian Tzolov
 * @since 1.0.0
 */
@Deprecated
public interface RequestResponseAdvisor extends CallAroundAdvisor, StreamAroundAdvisor {

	@Override
	default String getName() {
		return this.getClass().getSimpleName();
	}

	default AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> adviseContext) {
		return request;
	}

	default ChatResponse adviseResponse(ChatResponse response, Map<String, Object> adviseContext) {
		return response;
	}

	default Flux<ChatResponse> adviseResponse(Flux<ChatResponse> fluxResponse, Map<String, Object> context) {
		return fluxResponse;
	}

	@Override
	default AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
		var context = new HashMap<>(advisedRequest.adviseContext());
		var requestPrim = adviseRequest(advisedRequest, context);
		advisedRequest = AdvisedRequest.from(requestPrim)
			.withAdviseContext(Collections.unmodifiableMap(context))
			.build();

		var advisedResponse = chain.nextAroundCall(advisedRequest);

		context = new HashMap<>(advisedResponse.adviseContext());
		var chatResponse = adviseResponse(advisedResponse.response(), context);
		return new AdvisedResponse(chatResponse, Collections.unmodifiableMap(context));
	}

	default Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {

		ConcurrentHashMap<String, Object> context = new ConcurrentHashMap<>(advisedRequest.adviseContext());

		advisedRequest = adviseRequest(advisedRequest, context);

		var advisedResponseStream = chain.nextAroundStream(advisedRequest);

		return this.adviseResponse(advisedResponseStream.map(ar -> ar.response()), context)
			.map(chatResponse -> new AdvisedResponse(chatResponse, context));
	}

}
