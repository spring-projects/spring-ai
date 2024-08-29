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

import org.springframework.ai.chat.client.advisor.api.ResponseAdvisor;

import java.util.Map;

import org.springframework.ai.chat.client.advisor.api.RequestAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Advisor called before and after the {@link ChatModel#call(Prompt)} and
 * {@link ChatModel#stream(Prompt)} methods calls. The {@link ChatClient} maintains a
 * chain of advisors with shared advise context.
 *
 * @deprecated since 1.0.0 please use {@link RequestAdvisor}, {@link ResponseAdvisor}
 * instead.
 * @author Christian Tzolov
 * @since 1.0.0
 */
@Deprecated
public interface RequestResponseAdvisor extends RequestAdvisor, ResponseAdvisor {

	@Override
	default String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	default AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> adviseContext) {
		return request;
	}

	@Override
	default ChatResponse adviseResponse(ChatResponse response, Map<String, Object> adviseContext) {
		return response;
	}

}