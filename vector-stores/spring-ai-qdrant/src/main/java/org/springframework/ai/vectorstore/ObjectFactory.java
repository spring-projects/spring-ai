package org.springframework.ai.vectorstore;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.qdrant.client.grpc.JsonWithInt.ListValue;
import io.qdrant.client.grpc.JsonWithInt.Value;

/**
 * Utility methods for buidling Java objects from io.qdrant.client.grpc.JsonWithInt.Value.
 *
 */
class ObjectFactory {

	private ObjectFactory() {
	}

	public static Map<String, Object> map(Map<String, Value> payload) {
		Map<String, Object> hashMap = new HashMap<>(payload.size());
		for (Map.Entry<String, Value> entry : payload.entrySet()) {
			String fieldName = entry.getKey();
			Value fieldValue = entry.getValue();
			hashMap.put(fieldName, object(fieldValue));
		}

		return hashMap;
	}

	private static Object object(ListValue listValue) {
		return listValue.getValuesList().stream().map(ObjectFactory::object).collect(Collectors.toList());
	}

	private static Object object(Value value) {

		switch (value.getKindCase()) {
			case INTEGER_VALUE:
				return value.getIntegerValue();
			case STRING_VALUE:
				return value.getStringValue();
			case DOUBLE_VALUE:
				return value.getDoubleValue();
			case BOOL_VALUE:
				return value.hasBoolValue();
			case LIST_VALUE:
				return object(value.getListValue());
			case STRUCT_VALUE:
				return map(value.getStructValue().getFieldsMap());
			case KIND_NOT_SET:
			case NULL_VALUE:
			default:
				return null;
		}

	}

}
