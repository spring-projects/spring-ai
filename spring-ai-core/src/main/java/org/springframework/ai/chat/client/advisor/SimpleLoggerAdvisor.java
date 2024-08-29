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
package org.springframework.ai.chat.client.advisor;

import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.ResponseAdvisor;
import org.springframework.ai.chat.client.advisor.api.RequestAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.ModelOptionsUtils;

/**
 * A simple logger advisor that logs the request and response messages.
 *
 * @author Christian Tzolov
 */
public class SimpleLoggerAdvisor implements RequestAdvisor, ResponseAdvisor {

	private static final Logger logger = LoggerFactory.getLogger(SimpleLoggerAdvisor.class);

	public static final Function<AdvisedRequest, String> DEFAULT_REQUEST_TO_STRING = (request) -> {
		return request.toString();
	};

	public static final Function<ChatResponse, String> DEFAULT_RESPONSE_TO_STRING = (response) -> {
		return ModelOptionsUtils.toJsonString(response);
	};

	private final Function<AdvisedRequest, String> requestToString;

	private final Function<ChatResponse, String> responseToString;

	public SimpleLoggerAdvisor() {
		this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING);
	}

	public SimpleLoggerAdvisor(Function<AdvisedRequest, String> requestToString,
			Function<ChatResponse, String> responseToString) {
		this.requestToString = requestToString;
		this.responseToString = responseToString;
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
		logger.debug("request: {}", this.requestToString.apply(request));
		return request;
	}

	@Override
	public ChatResponse adviseResponse(ChatResponse response, Map<String, Object> context) {
		logger.debug("response: {}", this.responseToString.apply(response));
		return response;
	}

	@Override
	public String toString() {
		return SimpleLoggerAdvisor.class.getSimpleName();
	}

}
