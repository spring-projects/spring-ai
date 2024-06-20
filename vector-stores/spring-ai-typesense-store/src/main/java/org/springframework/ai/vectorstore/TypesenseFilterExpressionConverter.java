package org.springframework.ai.vectorstore;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

/**
 * Converts {@link Filter.Expression} into Typesense metadata filter expression format.
 * (https://typesense.org/docs/0.24.0/api/search.html#filter-parameters)
 *
 * @author Pablo Sanchidrian
 */
public class TypesenseFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected void doExpression(Filter.Expression exp, StringBuilder context) {
		this.convertOperand(exp.left(), context);
		context.append(getOperationSymbol(exp));
		this.convertOperand(exp.right(), context);
	}

	private String getOperationSymbol(Filter.Expression exp) {
		switch (exp.type()) {
			case AND:
				return " && ";
			case OR:
				return " || ";
			case EQ:
				return " "; // in typesense "EQ" operator looks like -> country:USA
			case NE:
				return " != ";
			case LT:
				return " < ";
			case LTE:
				return " <= ";
			case GT:
				return " > ";
			case GTE:
				return " >= ";
			case IN:
				return " "; // in typesense "IN" operator looks like -> country: [USA, UK]
			case NIN:
				return " != "; // in typesense "NIN" operator looks like -> country:
								// !=[USA, UK]
			default:
				throw new RuntimeException("Not supported expression type:" + exp.type());
		}
	}

	@Override
	protected void doGroup(Filter.Group group, StringBuilder context) {
		this.convertOperand(new Filter.Expression(Filter.ExpressionType.AND, group.content(), group.content()),
				context); // trick
	}

	@Override
	protected void doKey(Filter.Key key, StringBuilder context) {
		context.append("metadata." + key.key() + ":");
	}

}