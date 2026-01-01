/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import org.springframework.ai.google.genai.tts.GeminiTtsOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Google GenAI Text-to-Speech.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@ConfigurationProperties(GoogleGenAiTtsProperties.CONFIG_PREFIX)
public class GoogleGenAiTtsProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.tts";

	public static final String DEFAULT_MODEL = "gemini-2.5-flash-preview-tts";

	public static final String DEFAULT_VOICE = "Kore";

	/**
	 * Google GenAI TTS options.
	 */
	@NestedConfigurationProperty
	private final GeminiTtsOptions options = GeminiTtsOptions.builder()
		.model(DEFAULT_MODEL)
		.voice(DEFAULT_VOICE)
		.build();

	public GeminiTtsOptions getOptions() {
		return this.options;
	}

}
