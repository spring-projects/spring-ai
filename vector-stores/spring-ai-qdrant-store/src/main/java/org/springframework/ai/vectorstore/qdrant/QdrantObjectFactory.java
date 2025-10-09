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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.qdrant.client.grpc.JsonWithInt.ListValue;
import io.qdrant.client.grpc.JsonWithInt.Value;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;

/**
 * Utility methods for building Java objects from io.qdrant.client.grpc.JsonWithInt.Value.
 *
 * @author Anush Shetty
 * @author Heonwoo Kim
 * @since 0.8.1
 */
final class QdrantObjectFactory {

	private static final Log logger = LogFactory.getLog(QdrantObjectFactory.class);

	private QdrantObjectFactory() {
	}

	public static Map<String, Object> toObjectMap(Map<String, Value> payload) {
		Assert.notNull(payload, "Payload map must not be null");
		Map<String, Object> map = new HashMap<>();
		for (Map.Entry<String, Value> entry : payload.entrySet()) {
			map.put(entry.getKey(), object(entry.getValue()));
		}
		return map;
	}

	private static Object object(ListValue listValue) {
		return listValue.getValuesList().stream().map(QdrantObjectFactory::object).collect(Collectors.toList());
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
				return value.getBoolValue();
			case LIST_VALUE:
				return object(value.getListValue());
			case STRUCT_VALUE:
				return toObjectMap(value.getStructValue().getFieldsMap());
			case NULL_VALUE:
				return null;
			case KIND_NOT_SET:
			default:
				logger.warn("Unsupported value type: " + value.getKindCase());
				return null;
		}

	}

}
