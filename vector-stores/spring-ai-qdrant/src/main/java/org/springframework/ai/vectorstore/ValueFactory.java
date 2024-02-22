package org.springframework.ai.vectorstore;

import static io.qdrant.client.ValueFactory.list;
import static io.qdrant.client.ValueFactory.nullValue;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.qdrant.client.grpc.JsonWithInt.Struct;
import io.qdrant.client.grpc.JsonWithInt.Value;

/**
 * Utility methods for building io.qdrant.client.grpc.JsonWithInt.Value from Java objects.
 */
class ValueFactory {

	private ValueFactory() {
	}

	public static Map<String, Value> map(Map<String, Object> inputMap) {
		Map<String, Value> map = new HashMap<>(inputMap.size());
		for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
			String fieldName = entry.getKey();
			Object value = entry.getValue();
			map.put(fieldName, value(value));
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	private static Value value(Object value) {

		if (value == null) {
			return nullValue();
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
			return value((Map<String, Object>) value);
		}

		switch (value.getClass().getSimpleName()) {
			case "String":
				return io.qdrant.client.ValueFactory.value((String) value);
			case "Integer":
				return io.qdrant.client.ValueFactory.value((Integer) value);
			case "Double":
				return io.qdrant.client.ValueFactory.value((Double) value);
			case "Float":
				return io.qdrant.client.ValueFactory.value((Float) value);
			case "Boolean":
				return io.qdrant.client.ValueFactory.value((Boolean) value);
			default:
				throw new IllegalArgumentException("Unsupported Qdrant value type: " + value.getClass());
		}
	}

	private static Value value(Object[] elements) {
		List<Value> values = new ArrayList<Value>(elements.length);

		for (Object element : elements) {
			values.add(value(element));
		}

		return list(values);
	}

	private static Value value(Map<String, Object> inputMap) {
		Struct.Builder structBuilder = Struct.newBuilder();
		Map<String, Value> map = map(inputMap);
		structBuilder.putAllFields(map);
		return Value.newBuilder().setStructValue(structBuilder).build();
	}

}