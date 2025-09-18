/*
 * Copyright 2023-2024 the original author or authors.
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

import org.springframework.ai.observation.conventions.AiProvider;

/**
 * Constants for Google Gen AI.
 *
 * @author Soby Chacko
 */
public final class GoogleGenAiConstants {

	public static final String PROVIDER_NAME = AiProvider.GOOGLE_GENAI_AI.value();

	private GoogleGenAiConstants() {

	}

}
