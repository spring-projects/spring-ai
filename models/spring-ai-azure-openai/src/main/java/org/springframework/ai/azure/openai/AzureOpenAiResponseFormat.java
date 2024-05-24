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
package org.springframework.ai.azure.openai;

/**
 * Utility enumeration for representing the response format that may be requested from the
 * Azure OpenAI model. Please check <a href=
 * "https://platform.openai.com/docs/api-reference/chat/create#chat-create-response_format">OpenAI
 * API documentation</a> for more details.
 */
public enum AzureOpenAiResponseFormat {

	// default value used by OpenAI
	TEXT,
	/*
	 * From the OpenAI API documentation: Compatability: Compatible with GPT-4 Turbo and
	 * all GPT-3.5 Turbo models newer than gpt-3.5-turbo-1106. Caveats: This enables JSON
	 * mode, which guarantees the message the model generates is valid JSON. Important:
	 * when using JSON mode, you must also instruct the model to produce JSON yourself via
	 * a system or user message. Without this, the model may generate an unending stream
	 * of whitespace until the generation reaches the token limit, resulting in a
	 * long-running and seemingly "stuck" request. Also note that the message content may
	 * be partially cut off if finish_reason="length", which indicates the generation
	 * exceeded max_tokens or the conversation exceeded the max context length.
	 */
	JSON

}
