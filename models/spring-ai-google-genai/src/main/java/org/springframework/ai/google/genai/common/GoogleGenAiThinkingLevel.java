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
 * @author Dan Dobrin
 * @since 1.1.0
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
