/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.google.genai.common;

/**
 * Enum representing the level of thinking tokens the model should generate. This controls
 * the depth of reasoning the model applies during generation.
 *
 * <p>
 * <strong>Model Compatibility:</strong> This option is only supported by Gemini 3 Pro
 * models. For Gemini 2.5 series and earlier models, use
 * {@link org.springframework.ai.google.genai.GoogleGenAiChatOptions#getThinkingBudget()
 * thinkingBudget} instead.
 *
 * <p>
 * <strong>Important:</strong> {@code thinkingLevel} and {@code thinkingBudget} are
 * mutually exclusive. You cannot use both in the same request - doing so will result in
 * an API error.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 * @see <a href="https://ai.google.dev/gemini-api/docs/thinking">Google GenAI Thinking
 * documentation</a>
 */
public enum GoogleGenAiThinkingLevel {

	/**
	 * Unspecified thinking level. The model uses its default behavior.
	 */
	THINKING_LEVEL_UNSPECIFIED,

	/**
	 * Low thinking level. Minimal reasoning tokens are generated. Use for simple queries
	 * where speed is preferred over deep analysis.
	 */
	LOW,

	/**
	 * High thinking level. Extensive reasoning tokens are generated. Use for complex
	 * problems requiring deep analysis and step-by-step reasoning.
	 */
	HIGH

}
