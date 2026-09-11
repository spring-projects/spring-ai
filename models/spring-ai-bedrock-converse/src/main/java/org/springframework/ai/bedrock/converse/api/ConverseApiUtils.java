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

package org.springframework.ai.bedrock.converse.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.document.Document;

/**
 * Amazon Bedrock Converse API utils.
 *
 * @author Wei Jiang
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public final class ConverseApiUtils {

	private ConverseApiUtils() {
	}

	@SuppressWarnings("unchecked")
	public static Document convertObjectToDocument(Object value) {
		if (value == null) {
			return Document.fromNull();
		}
		else if (value instanceof String stringValue) {
			return Document.fromString(stringValue);
		}
		else if (value instanceof Boolean booleanValue) {
			return Document.fromBoolean(booleanValue);
		}
		else if (value instanceof Integer integerValue) {
			return Document.fromNumber(integerValue);
		}
		else if (value instanceof Long longValue) {
			return Document.fromNumber(longValue);
		}
		else if (value instanceof Float floatValue) {
			return Document.fromNumber(floatValue);
		}
		else if (value instanceof Double doubleValue) {
			return Document.fromNumber(doubleValue);
		}
		else if (value instanceof BigDecimal bigDecimalValue) {
			return Document.fromNumber(bigDecimalValue);
		}
		else if (value instanceof BigInteger bigIntegerValue) {
			return Document.fromNumber(bigIntegerValue);
		}
		else if (value instanceof List listValue) {
			return Document.fromList(listValue.stream().map(v -> convertObjectToDocument(v)).toList());
		}
		else if (value instanceof Map mapValue) {
			return convertMapToDocument(mapValue);
		}
		else {
			throw new IllegalArgumentException("Unsupported value type:" + value.getClass().getSimpleName());
		}
	}

	public static Map<String, String> getRequestMetadata(Map<String, Object> metadata) {

		if (metadata.isEmpty()) {
			return Map.of();
		}

		Map<String, String> result = new HashMap<>();
		for (Map.Entry<String, Object> entry : metadata.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();

			if (key != null && value != null) {
				result.put(key, value.toString());
			}
		}

		return result;
	}

	private static Document convertMapToDocument(Map<String, Object> value) {
		Map<String, Document> attr = value.entrySet()
			.stream()
			.collect(Collectors.toMap(e -> e.getKey(), e -> convertObjectToDocument(e.getValue())));

		return Document.fromMap(attr);
	}

	/**
	 * Converts an AWS SDK {@link Document} into a plain Java object ({@link Map},
	 * {@link List}, {@link String}, {@link Boolean}, {@link BigDecimal} or {@code null}).
	 * <p>
	 * This is the inverse of {@link #convertObjectToDocument(Object)}. It lets callers
	 * serialize a document to JSON with a real JSON writer instead of relying on
	 * {@link Document#toString()}, whose output is not valid JSON (for example, it does
	 * not escape control characters such as newlines inside string values).
	 * @param document the document to convert, may be {@code null}
	 * @return the equivalent plain Java object, or {@code null} for a null document
	 */
	public static @Nullable Object convertDocumentToObject(@Nullable Document document) {
		if (document == null || document.isNull()) {
			return null;
		}
		else if (document.isString()) {
			return document.asString();
		}
		else if (document.isBoolean()) {
			return document.asBoolean();
		}
		else if (document.isNumber()) {
			return document.asNumber().bigDecimalValue();
		}
		else if (document.isList()) {
			return document.asList().stream().map(ConverseApiUtils::convertDocumentToObject).toList();
		}
		else if (document.isMap()) {
			return convertDocumentToMap(document.asMap());
		}
		else {
			throw new IllegalArgumentException("Unsupported document type: " + document.getClass().getSimpleName());
		}
	}

	private static Map<String, Object> convertDocumentToMap(Map<String, Document> document) {
		Map<String, Object> result = new LinkedHashMap<>();
		document.forEach((key, value) -> result.put(key, convertDocumentToObject(value)));
		return result;
	}

}
