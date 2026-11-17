/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.tool.execution;

import java.lang.reflect.Type;

import org.jspecify.annotations.Nullable;

/**
 * A {@link ToolCallResultConverter} that returns {@link String} results unchanged.
 *
 * <p>
 * Non-String results are converted with {@link DefaultToolCallResultConverter}.
 *
 * @author Iuliia Sobolevska
 * @since 2.0.2
 */
public final class TextToolCallResultConverter implements ToolCallResultConverter {

	private static final ToolCallResultConverter DEFAULT_CONVERTER = new DefaultToolCallResultConverter();

	@Override
	public String convert(@Nullable Object result, @Nullable Type returnType) {
		if (result instanceof String text) {
			return text;
		}
		return DEFAULT_CONVERTER.convert(result, returnType);
	}

}
