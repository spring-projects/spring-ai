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

package org.springframework.ai.chat.client.advisor;

import java.util.function.Function;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.core.log.LogAccessor;

/**
 * A simple logger advisor that logs the request and response messages.
 *
 * @author Christian Tzolov
 */
public class SimpleLoggerAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

	public static final Function<AdvisedRequest, String> DEFAULT_REQUEST_TO_STRING = request -> request.toString();

	public static final Function<ChatResponse, String> DEFAULT_RESPONSE_TO_STRING = response -> ModelOptionsUtils
		.toJsonStringPrettyPrinter(response);

	private static final LogAccessor logger = new LogAccessor(SimpleLoggerAdvisor.class);

	private final Function<AdvisedRequest, String> requestToString;

	private final Function<ChatResponse, String> responseToString;

	private int order;

	public SimpleLoggerAdvisor() {
		this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, 0);
	}

	public SimpleLoggerAdvisor(int order) {
		this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, order);
	}

	public SimpleLoggerAdvisor(Function<AdvisedRequest, String> requestToString,
			Function<ChatResponse, String> responseToString, int order) {
		this.requestToString = requestToString;
		this.responseToString = responseToString;
		this.order = order;
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	private AdvisedRequest before(AdvisedRequest request) {
		logger.debug("request: " + this.requestToString.apply(request));
		return request;
	}

	private void observeAfter(AdvisedResponse advisedResponse) {
		logger.debug("response: " + this.responseToString.apply(advisedResponse.response()));
	}

	@Override
	public String toString() {
		return SimpleLoggerAdvisor.class.getSimpleName();
	}

	@Override
	public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {

		advisedRequest = before(advisedRequest);

		AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);

		observeAfter(advisedResponse);

		return advisedResponse;
	}

	@Override
	public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {

		advisedRequest = before(advisedRequest);

		Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest);

		return new MessageAggregator().aggregateAdvisedResponse(advisedResponses, this::observeAfter);
	}

}
