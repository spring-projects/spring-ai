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

package org.springframework.ai.vectorstore.weaviate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.weaviate.client6.v1.api.collections.query.Filter;
import io.weaviate.client6.v1.api.collections.query.Filter.FilterBuilder;
import io.weaviate.client6.v1.api.collections.query.FilterOperand;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Operand;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.util.Assert;

/**
 * Converts a Spring AI {@link Expression} into a native Weaviate {@code client6}
 * {@link Filter} object.
 *
 * <p>
 * Unlike the previous GraphQL-based implementation, this converter produces structured
 * {@link Filter} objects consumed directly by the client6 gRPC query and delete APIs;
 * there is no GraphQL filter string surface. Because the resulting {@link Filter} is a
 * third-party type, this converter is intentionally package-internal.
 *
 * @author Christian Tzolov
 * @author Jonghoon Park
 */
class WeaviateFilterExpressionConverter {

	// https://weaviate.io/developers/weaviate/api/graphql/filters#special-cases
	// The document id maps to Weaviate's system uuid field. The GraphQL
	// `_creationTimeUnix` / `_lastUpdateTimeUnix` special identifiers only ever existed
	// to support the removed `id != -1` group placeholder and are no longer accepted.
	private static final String SYSTEM_ID = "id";

	private static final List<String> SYSTEM_IDENTIFIERS = List.of(SYSTEM_ID);

	private static final String DEFAULT_META_FIELD_PREFIX = "meta_";

	private final List<String> allowedIdentifierNames;

	private final String metaFieldPrefix;

	WeaviateFilterExpressionConverter(List<String> allowedIdentifierNames) {
		this(allowedIdentifierNames, DEFAULT_META_FIELD_PREFIX);
	}

	WeaviateFilterExpressionConverter(List<String> allowedIdentifierNames, String metaFieldPrefix) {
		Assert.notNull(allowedIdentifierNames, "List can be empty but not null.");
		Assert.notNull(metaFieldPrefix, "metaFieldPrefix can be empty but not null.");
		this.allowedIdentifierNames = allowedIdentifierNames;
		this.metaFieldPrefix = metaFieldPrefix;
	}

	/**
	 * Converts the given Spring AI filter expression into a native Weaviate
	 * {@link Filter}.
	 * @param expression the Spring AI filter expression, must not be {@code null}
	 * @return the equivalent client6 {@link Filter}
	 */
	Filter convert(Expression expression) {
		Assert.notNull(expression, "Filter expression must not be null");
		return convertExpression(expression);
	}

	private Filter convertExpression(Expression exp) {
		return switch (exp.type()) {
			case AND -> convertBoolean(exp, true);
			case OR -> convertBoolean(exp, false);
			case EQ, NE, LT, LTE, GT, GTE -> convertComparison(exp);
			case IN -> convertIn(exp);
			case NIN -> convertNin(exp);
			default -> throw new UnsupportedOperationException("Not supported expression type: " + exp.type());
		};
	}

	private Filter convertBoolean(Expression exp, boolean and) {
		Operand right = exp.right();
		Assert.state(right != null, "AND/OR expression must have a right operand");
		Filter left = convertOperand(exp.left());
		Filter rightFilter = convertOperand(right);
		return and ? Filter.and(left, rightFilter) : Filter.or(left, rightFilter);
	}

	private Filter convertOperand(Operand operand) {
		if (operand instanceof Group group) {
			// A group is simply its nested expression; the GraphQL `id != -1` placeholder
			// hack is no longer needed with the structured Filter API.
			return convertExpression(group.content());
		}
		if (operand instanceof Expression expression) {
			return convertExpression(expression);
		}
		throw new IllegalArgumentException("Unsupported operand in boolean expression: " + operand);
	}

	private Filter convertComparison(Expression exp) {
		Assert.isInstanceOf(Key.class, exp.left(), "Comparison expression must have a Key as its left operand");
		Assert.isInstanceOf(Value.class, exp.right(), "Comparison expression must have a Value as its right operand");

		String identifier = identifierOf((Key) exp.left());
		Object value = ((Value) exp.right()).value();

		if (SYSTEM_ID.equals(identifier)) {
			return systemIdComparison(exp.type(), value);
		}
		return propertyComparison(withMetaPrefix(identifier), exp.type(), value);
	}

	private Filter propertyComparison(String path, ExpressionType type, Object value) {
		FilterBuilder property = Filter.property(path);
		if (value instanceof String string) {
			return switch (type) {
				case EQ -> property.eq(string);
				case NE -> property.ne(string);
				case LT -> property.lt(string);
				case LTE -> property.lte(string);
				case GT -> property.gt(string);
				case GTE -> property.gte(string);
				default -> throw unsupported(type, value);
			};
		}
		if (value instanceof Boolean bool) {
			return switch (type) {
				case EQ -> property.eq(bool.booleanValue());
				case NE -> property.ne(bool.booleanValue());
				default -> throw unsupported(type, value);
			};
		}
		if (value instanceof Number number) {
			// Filterable numeric metadata fields are created as Weaviate `number`
			// (float64) properties, mirroring the legacy `valueNumber` behavior.
			double d = number.doubleValue();
			return switch (type) {
				case EQ -> property.eq(d);
				case NE -> property.ne(d);
				case LT -> property.lt(d);
				case LTE -> property.lte(d);
				case GT -> property.gt(d);
				case GTE -> property.gte(d);
				default -> throw unsupported(type, value);
			};
		}
		if (value instanceof Date || value instanceof OffsetDateTime) {
			OffsetDateTime dateTime = toOffsetDateTime(value);
			return switch (type) {
				case EQ -> property.eq(dateTime);
				case NE -> property.ne(dateTime);
				case LT -> property.lt(dateTime);
				case LTE -> property.lte(dateTime);
				case GT -> property.gt(dateTime);
				case GTE -> property.gte(dateTime);
				default -> throw unsupported(type, value);
			};
		}
		throw new RuntimeException("Unsupported value type: " + (value == null ? "null" : value.getClass()));
	}

	private Filter systemIdComparison(ExpressionType type, Object value) {
		String id = String.valueOf(value);
		return switch (type) {
			case EQ -> Filter.uuid().eq(id);
			case NE -> Filter.uuid().ne(id);
			default -> throw unsupported(type, value);
		};
	}

	private Filter convertIn(Expression exp) {
		return Filter.or(expandLeaves(exp, ExpressionType.EQ).toArray(new FilterOperand[0]));
	}

	private Filter convertNin(Expression exp) {
		return Filter.and(expandLeaves(exp, ExpressionType.NE).toArray(new FilterOperand[0]));
	}

	private List<Filter> expandLeaves(Expression exp, ExpressionType leafType) {
		Assert.isInstanceOf(Key.class, exp.left(), "IN/NIN expression must have a Key as its left operand");
		Assert.isInstanceOf(Value.class, exp.right(), "IN/NIN expression must have a Value as its right operand");

		String identifier = identifierOf((Key) exp.left());
		Object value = ((Value) exp.right()).value();

		List<?> values = (value instanceof List<?> list) ? list : List.of(value);
		Assert.notEmpty(values, "IN/NIN expression must have at least one value");

		List<Filter> leaves = new ArrayList<>(values.size());
		for (Object element : values) {
			leaves.add(convertComparison(new Expression(leafType, new Key(identifier), new Value(element))));
		}
		return leaves;
	}

	private String identifierOf(Key key) {
		return hasOuterQuotes(key.key()) ? removeOuterQuotes(key.key()) : key.key();
	}

	String withMetaPrefix(String identifier) {
		if (SYSTEM_IDENTIFIERS.contains(identifier)) {
			return identifier;
		}

		if (this.allowedIdentifierNames.contains(identifier)) {
			return this.metaFieldPrefix + identifier;
		}

		throw new IllegalArgumentException("Not allowed filter identifier name: " + identifier
				+ ". Consider adding it to WeaviateVectorStore#filterMetadataFields.");
	}

	private static OffsetDateTime toOffsetDateTime(Object value) {
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime;
		}
		if (value instanceof Date date) {
			return date.toInstant().atOffset(ZoneOffset.UTC);
		}
		throw new RuntimeException("Unsupported date value type: " + (value == null ? "null" : value.getClass()));
	}

	private static RuntimeException unsupported(ExpressionType type, Object value) {
		return new UnsupportedOperationException(
				"Unsupported operator '" + type + "' for value type: " + (value == null ? "null" : value.getClass()));
	}

	private static boolean hasOuterQuotes(String value) {
		String trimmed = value.trim();
		return (trimmed.startsWith("\"") && trimmed.endsWith("\""))
				|| (trimmed.startsWith("'") && trimmed.endsWith("'"));
	}

	private static String removeOuterQuotes(String in) {
		return in.substring(1, in.length() - 1);
	}

}
