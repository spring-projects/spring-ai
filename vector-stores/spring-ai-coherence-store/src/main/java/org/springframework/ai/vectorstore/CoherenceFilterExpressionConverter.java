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

package org.springframework.ai.vectorstore;

import java.util.List;

import com.tangosol.util.Filter;
import com.tangosol.util.Filters;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.extractor.ChainedExtractor;
import com.tangosol.util.extractor.UniversalExtractor;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Operand;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterHelper;

/**
 * Converts Spring AI {@link Expression} into Coherence {@link Filter}.
 *
 * @author Aleks Seovic
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
			case NOT -> convert(FilterHelper.negate(expression));
			case AND -> and(expression);
			case OR -> or(expression);
		};
	}

	private Filter<?> and(Expression expression) {
		Filter<?> left = convert(expression.left());
		Filter<?> right = convert(expression.right());
		return left.and(right);
	}

	private Filter<?> or(Expression expression) {
		Filter<?> left = convert(expression.left());
		Filter<?> right = convert(expression.right());
		return left.or(right);
	}

	private ValueExtractor extractor(Operand op) {
		return new ChainedExtractor(new UniversalExtractor<>("metadata"), new UniversalExtractor<>(((Key) op).key()));
	}

	private <T> T value(Operand op) {
		return (T) ((Value) op).value();
	}

}
