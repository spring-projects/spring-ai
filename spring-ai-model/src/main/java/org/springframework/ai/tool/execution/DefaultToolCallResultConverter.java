/*
 * Copyright 2023-2025 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.util.json.JsonParser;
import org.springframework.lang.Nullable;

/**
 * A default implementation of {@link ToolCallResultConverter}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class DefaultToolCallResultConverter implements ToolCallResultConverter {

	private static final Logger logger = LoggerFactory.getLogger(DefaultToolCallResultConverter.class);

	@Override
	public String convert(@Nullable Object result, @Nullable Type returnType) {
		if (returnType == Void.TYPE) {
			logger.debug("The tool has no return type. Converting to conventional response.");
			return "Done";
		}
		else {
			logger.debug("Converting tool result to JSON.");
			return JsonParser.toJson(result);
		}
	}

}
