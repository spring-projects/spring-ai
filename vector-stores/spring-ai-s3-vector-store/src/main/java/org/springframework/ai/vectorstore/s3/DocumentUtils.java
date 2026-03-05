/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.vectorstore.s3;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.document.Document;

/**
 * Helper class to convert from AWS SDK Document to Object and vice versa.
 *
 * @author Matej Nedic
 */
public final class DocumentUtils {

	private DocumentUtils() {
	}

	public static Document toDocument(@Nullable Object obj) {
		if (obj == null) {
			return Document.fromNull();
		}
		else if (obj instanceof String) {
			return Document.fromString((String) obj);
		}
		else if (obj instanceof Integer) {
			return Document.fromNumber((Integer) obj);
		}
		else if (obj instanceof Long) {
			return Document.fromNumber((Long) obj);
		}
		else if (obj instanceof Double) {
			return Document.fromNumber((Double) obj);
		}
		else if (obj instanceof Float) {
			return Document.fromNumber((Float) obj);
		}
		else if (obj instanceof Short) {
			return Document.fromNumber((Short) obj);
		}
		else if (obj instanceof Byte) {
			return Document.fromNumber((Byte) obj);
		}
		else if (obj instanceof BigDecimal) {
			return Document.fromNumber((BigDecimal) obj);
		}
		else if (obj instanceof BigInteger) {
			return Document.fromNumber((BigInteger) obj);
		}
		else if (obj instanceof Boolean) {
			return Document.fromBoolean((Boolean) obj);
		}
		else if (obj instanceof Map<?, ?> map) {
			Document.MapBuilder mapBuilder = Document.mapBuilder();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				String key = entry.getKey().toString();
				Document valueDoc = toDocument(entry.getValue());
				mapBuilder.putDocument(key, valueDoc);
			}
			return mapBuilder.build();
		}
		else {
			Collection<?> collection = (Collection<?>) obj;
			Document.ListBuilder listDoc = Document.listBuilder();
			for (Object item : collection) {
				listDoc.addDocument(toDocument(item));
			}
			return listDoc.build();
		}
	}

	public static @Nullable Map<String, Object> fromDocument(Document document) {
		if (document.isNull()) {
			return null;
		}
		Map<String, Document> mapDocs = document.asMap();
		Map<String, Object> mapMetadata = new HashMap<>(mapDocs.size());
		for (Map.Entry<String, Document> entry : mapDocs.entrySet()) {
			mapMetadata.put(entry.getKey(), fromDocumentToObject(entry.getValue()));
		}
		return mapMetadata;
	}

	private static @Nullable Object fromDocumentToObject(Document document) {
		if (document.isNull()) {
			return null;
		}
		else if (document.isString()) {
			return document.asString();
		}
		else if (document.isNumber()) {
			// This is same problem DynamoDB sdk has. I am in favour of returning
			// BigDecimal because of floats.
			return document.asNumber().bigDecimalValue();
		}
		else if (document.isBoolean()) {
			return document.asBoolean();
		}
		else if (document.isList()) {
			List<Document> docs = document.asList();
			List<Object> listMetadata = new ArrayList<>(docs.size());
			for (Document item : docs) {
				listMetadata.add(fromDocument(item));
			}
			return listMetadata;
		}
		else {
			return fromDocument(document);
		}
	}

}
