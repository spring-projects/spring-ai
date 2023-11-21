/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.openai.client;

import reactor.core.publisher.Flux;

import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.messages.UserMessage;

public interface AiStreamClient {

	default Flux<OpenAiSseResponse> generateStream(String message) {
		Prompt prompt = new Prompt(new UserMessage(message));
		return generateStream(prompt);
	}

	Flux<OpenAiSseResponse> generateStream(Prompt prompt);

}
