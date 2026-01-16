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

package org.springframework.ai.vectorstore.qdrant;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.JsonWithInt.Struct;
import io.qdrant.client.grpc.JsonWithInt.Value;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Utility methods for building io.qdrant.client.grpc.JsonWithInt.Value from Java objects.
 *
 * @author Anush Shetty
 * @author Ilayaperumal Gopinathan
 * @since 0.8.1
 */
final class QdrantValueFactory {

	private QdrantValueFactory() {
	}

	public static Map<String, Value> toValueMap(Map<String, ? extends @Nullable Object> inputMap) {
		Assert.notNull(inputMap, "Input map must not be null");

		return inputMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> value(e.getValue())));
	}

	@SuppressWarnings("unchecked")
	private static Value value(@Nullable Object value) {

		if (value == null) {
			return ValueFactory.nullValue();
		}

		if (value.getClass().isArray()) {
			int length = Array.getLength(value);
			Object[] objectArray = new Object[length];
			for (int i = 0; i < length; i++) {
				objectArray[i] = Array.get(value, i);
			}
			return value(objectArray);
		}

		if (value instanceof Map) {
			return value((Map<String, @Nullable Object>) value);
		}

		if (value instanceof List) {
			return value((List<Object>) value);
		}

		return switch (value.getClass().getSimpleName()) {
			case "String" -> ValueFactory.value((String) value);
			case "Integer" -> ValueFactory.value((Integer) value);
			case "Long" ->
				// use String representation
				ValueFactory.value(String.valueOf(value));
			case "Double" -> ValueFactory.value((Double) value);
			case "Float" -> ValueFactory.value((Float) value);
			case "Boolean" -> ValueFactory.value((Boolean) value);
			default -> throw new IllegalArgumentException("Unsupported Qdrant value type: " + value.getClass());
		};
	}

	private static Value value(List<Object> elements) {
		List<Value> values = new ArrayList<>(elements.size());

		for (Object element : elements) {
			values.add(value(element));
		}

		return ValueFactory.list(values);
	}

	private static Value value(Object[] elements) {
		List<Value> values = new ArrayList<>(elements.length);

		for (Object element : elements) {
			values.add(value(element));
		}

		return ValueFactory.list(values);
	}

	private static Value value(Map<String, @Nullable Object> inputMap) {
		Struct.Builder structBuilder = Struct.newBuilder();
		Map<String, Value> map = toValueMap(inputMap);
		structBuilder.putAllFields(map);
		return Value.newBuilder().setStructValue(structBuilder).build();
	}

}
