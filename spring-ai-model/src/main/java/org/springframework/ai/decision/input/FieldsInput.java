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

package org.springframework.ai.decision.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.content.Media;
import org.springframework.util.Assert;

/**
 * A {@link DecisionInput} made of named fields, sent to the model as a JSON object. A
 * value can be a string, a number, a boolean, {@code null}, a map, a list, or, at the top
 * level only, a {@link Media}.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class FieldsInput implements DecisionInput {

	private final Map<String, @Nullable Object> fields;

	FieldsInput(Map<String, @Nullable Object> fields) {
		Assert.notEmpty(fields, "fields cannot be null or empty");
		fields.forEach((name, value) -> {
			Assert.hasText(name, "field names cannot be null or empty");
			validateValue(name, value, true);
		});
		this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
	}

	private static void validateValue(String name, @Nullable Object value, boolean topLevel) {
		if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
			return;
		}
		if (value instanceof Media) {
			Assert.isTrue(topLevel, () -> "field '" + name + "': media is only supported at the top level");
			return;
		}
		if (value instanceof Map<?, ?> map) {
			map.forEach((key, nested) -> {
				Assert.isInstanceOf(String.class, key, () -> "field '" + name + "': map keys must be strings");
				validateValue(name, nested, false);
			});
			return;
		}
		if (value instanceof List<?> list) {
			list.forEach(nested -> validateValue(name, nested, false));
			return;
		}
		throw new IllegalArgumentException("field '" + name + "': unsupported value type " + value.getClass().getName()
				+ ", use strings, numbers, booleans, maps, lists or media");
	}

	public Map<String, @Nullable Object> getFields() {
		return this.fields;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof FieldsInput that)) {
			return false;
		}
		return this.fields.equals(that.fields);
	}

	@Override
	public int hashCode() {
		return this.fields.hashCode();
	}

	@Override
	public String toString() {
		return "FieldsInput{fields=" + this.fields.keySet() + "}";
	}

}
