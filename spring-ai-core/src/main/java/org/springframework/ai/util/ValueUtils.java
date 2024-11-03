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

package org.springframework.ai.util;

import java.util.function.Function;

import org.springframework.util.StringUtils;

/**
 * Abstract utility class for process values.
 *
 * @author John Blum
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public abstract class ValueUtils {

	protected static final String EMPTY_STRING = "";

	public static <T> T defaultIfNull(T value, T defaultValue) {
		return value != null ? value : defaultValue;
	}

	public static <T> String defaultToEmptyString(T target, Function<T, String> transform) {
		String value = target != null ? transform.apply(target) : null;
		return defaultToEmptyString(value);
	}

	public static String defaultToEmptyString(String value) {
		return StringUtils.hasText(value) ? value : EMPTY_STRING;
	}

}
