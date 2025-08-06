package org.springframework.ai.vectorstore.s3;

import software.amazon.awssdk.core.document.Document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public class DocumentUtils {

	public static Document toDocument(Object obj) {
		if (obj == null) {
			return Document.fromNull();
		} else if (obj instanceof String) {
			return Document.fromString((String) obj);
		} else if (obj instanceof Integer) {
			return Document.fromNumber((Integer) obj);
		} else if (obj instanceof Long) {
			return Document.fromNumber((Long) obj);
		} else if (obj instanceof Double) {
			return Document.fromNumber((Double) obj);
		} else if (obj instanceof Float) {
			return Document.fromNumber((Float) obj);
		} else if (obj instanceof Short) {
			return Document.fromNumber((Short) obj);
		} else if (obj instanceof Byte) {
			return Document.fromNumber((Byte) obj);
		} else if (obj instanceof BigDecimal) {
			return Document.fromNumber((BigDecimal) obj);
		} else if (obj instanceof BigInteger) {
			return Document.fromNumber((BigInteger) obj);
		} else if (obj instanceof Boolean) {
			return Document.fromBoolean((Boolean) obj);
		} else if (obj instanceof Map<?, ?> map) {
			Document.MapBuilder mapBuilder = Document.mapBuilder();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				String key = entry.getKey().toString();
				Document valueDoc = toDocument(entry.getValue());
				mapBuilder.putDocument(key, valueDoc);
			}
			return mapBuilder.build();
		} else {
			Collection<?> collection = (Collection<?>) obj;
			Document.ListBuilder listDoc = Document.listBuilder();
			for (Object item : collection) {
				listDoc.addDocument(toDocument(item));
			}
			return listDoc.build();
		}
	}

	public static Map<String, Object> fromDocument (Document document) {
		if (document.isNull()) {
			return null;
		}
		Map<String, Document> mapDocs = document.asMap();
		Map<String, Object> mapMetadata = new HashMap<>(mapDocs.size());
		for (Map.Entry<String, Document> entry : mapDocs.entrySet()) {
			mapMetadata.put(entry.getKey(), fromDocument(entry.getValue()));
		}
		return mapMetadata;
	}

	private static Object fromDocumentToObject(Document document) {
		if (document.isNull()) {
			return null;
		} else if (document.isString()) {
			return document.asString();
		} else if (document.isNumber()) {
			// This is same problem DynamoDB sdk has. I am in favour of returning BigDecimal because of floats.
			return document.asNumber().bigDecimalValue();
		} else if (document.isBoolean()) {
			return document.asBoolean();
		} else if (document.isList()) {
			List<Document> docs = document.asList();
			List<Object> listMetadata = new ArrayList<>(docs.size());
			for (Document item : docs) {
				listMetadata.add(fromDocument(item));
			}
			return listMetadata;
		} else {
			return fromDocument(document);
		}
	}
}
