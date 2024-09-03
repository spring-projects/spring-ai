/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.chat.model;

import java.util.Arrays;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.StreamingModel;

@FunctionalInterface
public interface StreamingChatModel extends StreamingModel<Prompt, ChatResponse> {

	default Flux<String> stream(String message) {
		Prompt prompt = new Prompt(message);
		return stream(prompt).map(response -> (response.getResult() == null || response.getResult().getOutput() == null
				|| response.getResult().getOutput().getContent() == null) ? ""
						: response.getResult().getOutput().getContent());
	}

	default Flux<String> stream(Message... messages) {
		Prompt prompt = new Prompt(Arrays.asList(messages));
		return stream(prompt).map(response -> (response.getResult() == null || response.getResult().getOutput() == null
				|| response.getResult().getOutput().getContent() == null) ? ""
						: response.getResult().getOutput().getContent());
	}

	@Override
	Flux<ChatResponse> stream(Prompt prompt);

}
