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

import org.springframework.ai.chat.client.ChatClient.ChatClientRequest;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A before and after advisor called before the {@link ChatClientRequest} is sealed into a
 * {@link Prompt} and send and after the {@link ChatResponse} (or it Flux variant) is
 * received.
 *
 * @author Christian Tzolov
 * @since 1.0.0 M1
 */
public interface RequestResponseAdvisor {

	default AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
		return request;
	}

	default ChatResponse adviseResponse(ChatResponse response, Map<String, Object> context) {
		return response;
	}

	default Flux<ChatResponse> adviseResponse(Flux<ChatResponse> fluxResponse, Map<String, Object> context) {
		return fluxResponse;
	}

}
