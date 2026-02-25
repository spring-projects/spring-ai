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

package org.springframework.ai.model.ollama.autoconfigure;

import org.jspecify.annotations.NonNull;

import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.core.convert.converter.Converter;

/**
 * {@link Converter} handling string to {@link ThinkOption} conversion. Both
 * {@link ThinkOption.ThinkBoolean} and {@link ThinkOption.ThinkLevel} are supported.
 *
 * @author Nicolas Krier
 * @since 1.1.3
 */
public class ThinkOptionConverter implements Converter<String, ThinkOption> {

	@Override
	public ThinkOption convert(@NonNull String source) {
		return switch (source) {
			case "enabled" -> ThinkOption.ThinkBoolean.ENABLED;
			case "disabled" -> ThinkOption.ThinkBoolean.DISABLED;
			case "low" -> ThinkOption.ThinkLevel.LOW;
			case "medium" -> ThinkOption.ThinkLevel.MEDIUM;
			case "high" -> ThinkOption.ThinkLevel.HIGH;
			default -> throw new IllegalStateException("Unexpected think option value: " + source);
		};
	}

}
