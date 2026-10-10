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

package org.springframework.ai.mcp.annotation.method.resource;

import org.jspecify.annotations.Nullable;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.Assert;

/**
 * Converts URI template variable values to method parameter types.
 *
 * @author yubishen
 */
final class McpUriVariableValueConverter {

	private static final ConversionService CONVERSION_SERVICE = new DefaultConversionService();

	private McpUriVariableValueConverter() {
	}

	static Object convert(@Nullable String value, Class<?> targetType) {
		Assert.notNull(targetType, "targetType cannot be null");

		if (String.class.equals(targetType)) {
			return value;
		}

		try {
			Object converted = CONVERSION_SERVICE.convert(value, targetType);
			if (converted == null && value != null) {
				throw new ConversionFailedException(TypeDescriptor.valueOf(String.class),
						TypeDescriptor.valueOf(targetType), value,
						new IllegalArgumentException("Conversion returned null"));
			}
			return converted;
		}
		catch (ConversionFailedException ex) {
			throw new IllegalArgumentException(
					"Failed to convert URI variable value '%s' to %s".formatted(value, targetType.getName()), ex);
		}
	}

}
