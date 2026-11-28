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

package org.springframework.ai.vectorstore.coherence;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.oracle.coherence.ai.DocumentChunk;
import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;
import com.tangosol.util.Filter;
import com.tangosol.util.Filters;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.extractor.ChainedExtractor;
import com.tangosol.util.extractor.UniversalExtractor;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Operand;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterHelper;
import org.springframework.util.Assert;

/**
 * Converts Spring AI {@link Expression} into Coherence {@link Filter}.
 *
 * @author Aleks Seovic
 * @author Dimitar Proynov
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class CoherenceFilterExpressionConverter {

	public Filter<?> convert(Operand expression) {
		if (expression instanceof Expression) {
			return convert((Expression) expression);
		}
		return convert((Group) expression);
	}

	private Filter<?> convert(Group group) {
		return convert(group.content());
	}

	private Filter<?> convert(Expression expression) {
		if (expression.type() == ExpressionType.NOT) {
			return convert(FilterHelper.negate(expression));
		}
		Assert.state(expression.right() != null, "expression is expected to have a right operand");
		return switch (expression.type()) {
			case EQ -> Filters.equal(extractor(expression.left()), value(expression.right()));
			case NE -> Filters.notEqual(extractor(expression.left()), value(expression.right()));
			case GT -> Filters.greater(extractor(expression.left()), value(expression.right()));
			case GTE -> Filters.greaterEqual(extractor(expression.left()), value(expression.right()));
			case LT -> Filters.less(extractor(expression.left()), value(expression.right()));
			case LTE -> Filters.lessEqual(extractor(expression.left()), value(expression.right()));
			case IN -> Filters.in(extractor(expression.left()), ((List) value(expression.right())).toArray());
			case NIN ->
				Filters.not(Filters.in(extractor(expression.left()), ((List) value(expression.right())).toArray()));
			case AND -> Filters.all(convert(expression.left()), convert(expression.right()));
			case OR -> Filters.any(convert(expression.left()), convert(expression.right()));
			default -> throw new IllegalStateException("Unexpected value: " + expression.type());
		};
	}

	private ValueExtractor extractor(Operand op) {
		final String operandKey = ((Key) op).key();
		// UniversalExtractor resolves a plain identifier by first trying to invoke a
		// same-named (or bean-accessor-prefixed) zero-arg method via reflection, and
		// only falls back to a literal Map.get(key) lookup if no such method exists.
		// Neither extractor below uses reflection: MetadataExtractor calls
		// DocumentChunk#metadata() directly and MetadataKeyExtractor performs a literal
		// map lookup, so the key can never be interpreted as a method name.
		return new ChainedExtractor(MetadataExtractor.INSTANCE, new MetadataKeyExtractor(operandKey));
	}

	private <T> T value(Operand op) {
		return (T) ((Value) op).value();
	}

	/**
	 * A {@link ValueExtractor} that returns the metadata {@link Map} of a
	 * {@link DocumentChunk} through a direct {@link DocumentChunk#metadata()} call,
	 * without the reflective accessor resolution that {@link UniversalExtractor} would
	 * perform. Paired with {@link MetadataKeyExtractor} so that the whole metadata lookup
	 * chain is reflection-free.
	 */
	public static final class MetadataExtractor
			implements ValueExtractor<Object, Object>, PortableObject, Serializable {

		static final MetadataExtractor INSTANCE = new MetadataExtractor();

		private static final String CANONICAL_NAME = "metadata";

		public MetadataExtractor() {
		}

		@Override
		public @Nullable Object extract(Object target) {
			return target instanceof DocumentChunk chunk ? chunk.metadata() : null;
		}

		@Override
		public String getCanonicalName() {
			// Explicitly overridden for the same reason as in MetadataKeyExtractor
			return CANONICAL_NAME;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof MetadataExtractor;
		}

		@Override
		public int hashCode() {
			return CANONICAL_NAME.hashCode();
		}

		@Override
		public void readExternal(PofReader in) throws IOException {
		}

		@Override
		public void writeExternal(PofWriter out) throws IOException {
		}

		@Override
		public String toString() {
			return "MetadataExtractor()";
		}

	}

	/**
	 * A {@link ValueExtractor} that performs a literal {@link Map#get(Object)} lookup on
	 * its target, without any reflection-based method or bean-accessor resolution. Used
	 * instead of {@link UniversalExtractor} for user-controlled metadata field names, so
	 * that a name colliding with a {@link Map} method (for example {@code size},
	 * {@code empty}, or {@code class}) is never invoked as a method.
	 */
	public static final class MetadataKeyExtractor
			implements ValueExtractor<Object, Object>, PortableObject, Serializable {

		private String key;

		public MetadataKeyExtractor() {
			// required by POF; the key is populated by readExternal(PofReader)
			this.key = "";
		}

		MetadataKeyExtractor(String key) {
			this.key = key;
		}

		@Override
		public @Nullable Object extract(Object target) {
			return target instanceof Map<?, ?> map ? map.get(this.key) : null;
		}

		@Override
		public String getCanonicalName() {
			// Superclass's getCanonicalName() defaults to inspecting the target as a
			// serialized lambda, which throws IllegalArgumentException for a plain
			// class such as this one. Coherence's internal equals() helpers silently
			// swallow that RuntimeException and treat the extractors as unequal, so an
			// explicit override is required for equals()/hashCode()
			return this.key;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof MetadataKeyExtractor that && this.key.equals(that.key);
		}

		@Override
		public int hashCode() {
			return this.key.hashCode();
		}

		@Override
		public void readExternal(PofReader in) throws IOException {
			this.key = in.readString(0);
		}

		@Override
		public void writeExternal(PofWriter out) throws IOException {
			out.writeString(0, this.key);
		}

		@Override
		public String toString() {
			return "MetadataKeyExtractor(" + this.key + ")";
		}

	}

}
