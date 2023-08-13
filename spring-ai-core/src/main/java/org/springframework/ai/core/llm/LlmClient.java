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

package org.springframework.ai.core.llm;

import org.springframework.ai.core.prompt.Prompt;

public interface LlmClient {

	String generate(String text);

	// TODO Change to LLMResponse, maybe get rid of the varargs to simplify the response
	// object, convenience of batch query isn't worth adding the complexity to the
	// response object signature
	LLMResponse generate(Prompt prompt);

}
