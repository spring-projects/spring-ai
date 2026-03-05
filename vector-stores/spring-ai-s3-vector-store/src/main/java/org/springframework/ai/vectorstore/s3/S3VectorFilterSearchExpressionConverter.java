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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

import software.amazon.awssdk.core.SdkNumber;
import software.amazon.awssdk.core.document.Document;

import org.springframework.ai.vectorstore.filter.Filter;

/**
 * Default implementation of {@link S3VectorFilterExpressionConverter}
 *
 * @author Matej Nedic
 */
public class S3VectorFilterSearchExpressionConverter implements S3VectorFilterExpressionConverter {

	private final SimpleDateFormat dateFormat;

	public S3VectorFilterSearchExpressionConverter() {
		this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private String getOperationSymbol(Filter.ExpressionType exp) {
		return switch (exp) {
			case AND -> "$and";
			case NOT -> "$not";
			case OR -> "$or";
			case EQ -> "$eq";
			case NE -> "$ne";
			case LT -> "$lt";
			case LTE -> "$lte";
			case GT -> "$gt";
			case GTE -> "$gte";
			case NIN -> "$nin";
			case IN -> "$in";
			default -> throw new UnsupportedOperationException("Not supported expression type: " + exp);
		};
	}

	@Override
	public Document convertExpression(Filter.Expression expression) {
		String operationType = getOperationSymbol(expression.type());
		switch (expression.type()) {
			case EQ:
			case NE:
			case GTE:
			case GT:
			case LTE:
			case LT:
				return Document.fromMap(Map.of(((Filter.Key) expression.left()).key(), Document
					.fromMap(Map.of(operationType, wrapValue(Objects.requireNonNull(expression.right()))))));

			case IN:
			case NIN:
				Document document = wrapValue(Objects.requireNonNull(expression.right()));
				return Document.fromMap(Map.of(((Filter.Key) expression.left()).key(),
						Document.fromMap(Map.of(operationType, document))));

			case AND:
			case OR:
				Document leftDocument = wrapValue(Objects.requireNonNull(expression.left()));
				Document rightDocument = wrapValue(Objects.requireNonNull(expression.right()));
				return Document.fromMap(Map.of(operationType, Document.fromList(List.of(leftDocument, rightDocument))));

			default:
				throw new UnsupportedOperationException("Unsupported operator: " + expression.type());
		}
	}

	private Document wrapValue(Filter.Operand operand) {
		if (operand instanceof Filter.Value) {
			return convertToDocument(((Filter.Value) operand).value());
		}
		else if (operand instanceof Filter.Key) {
			return Document.fromString(((Filter.Key) operand).key());
		}
		else if (operand instanceof Filter.Group) {
			Filter.Expression expression = ((Filter.Group) operand).content();
			return convertExpression(expression);
		}
		else {
			return convertExpression((Filter.Expression) operand);
		}
	}

	private Document convertToDocument(Object value) {
		if (value instanceof String s) {
			return Document.fromString(s);
		}
		if (value instanceof Boolean b) {
			return Document.fromBoolean(b);
		}
		if (value instanceof Number n) {
			return Document.fromNumber(toSdkNumber(n));
		}
		if (value instanceof Date d) {
			return Document.fromString(this.dateFormat.format(d));
		}
		if (value instanceof List<?> list) {
			List<Document> converted = list.stream().map(this::convertToDocument).toList();
			return Document.fromList(converted);
		}
		return Document.fromString(String.valueOf(value));
	}

	private SdkNumber toSdkNumber(Number num) {
		if (num instanceof BigDecimal bd) {
			return SdkNumber.fromBigDecimal(bd);
		}
		if (num instanceof Integer i) {
			return SdkNumber.fromInteger(i);
		}
		if (num instanceof Long l) {
			return SdkNumber.fromLong(l);
		}
		if (num instanceof Double d) {
			return SdkNumber.fromDouble(d);
		}
		if (num instanceof Float f) {
			return SdkNumber.fromFloat(f);
		}
		if (num instanceof Short s) {
			return SdkNumber.fromShort(s);
		}
		if (num instanceof Byte b) {
			return SdkNumber.fromInteger(b.intValue());
		}
		throw new IllegalArgumentException("Unsupported Number type: " + num.getClass());
	}

}
