package org.springframework.ai.vectorstore;

import com.tangosol.util.Filter;
import com.tangosol.util.Filters;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.extractor.ChainedExtractor;
import com.tangosol.util.extractor.UniversalExtractor;

import org.springframework.ai.vectorstore.filter.Filter.*;
import org.springframework.ai.vectorstore.filter.FilterHelper;

import java.util.Collection;
import java.util.List;

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
