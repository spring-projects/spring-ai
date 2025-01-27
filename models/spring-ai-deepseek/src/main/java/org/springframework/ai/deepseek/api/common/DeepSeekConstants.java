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
package org.springframework.ai.deepseek.api.common;

import org.springframework.ai.observation.conventions.AiProvider;

/**
 * @author Geng Rong
 */
public class DeepSeekConstants {

	public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

	public static final String DEFAULT_COMPLETIONS_PATH = "/chat/completions";

	public static final String DEFAULT_BETA_PATH = "/beta";

	public static final String PROVIDER_NAME = AiProvider.DEEPSEEK.value();

	private DeepSeekConstants() {

	}

}
