package org.springframework.ai.vectorstore;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

/**
 * @author Loïc Lefèvre
 * @see <a href=
 * "https://docs.oracle.com/en/database/oracle/oracle-database/23/adjsn/json-path-expressions.html#GUID-8656CAB9-C293-4A99-BB62-F38F3CFC4C13">JSON
 * Path Documentation</a>
 */
public class ISOSQLJSONPathFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected String convertOperand(final Filter.Operand operand) {
		final StringBuilder context = new StringBuilder();
		context.append("$?( ");
		this.convertOperand(operand, context);
		return context.append(" )").toString();
	}

	@Override
	protected void doExpression(final Filter.Expression expression, final StringBuilder context) {
		if (expression.type() == Filter.ExpressionType.NIN) {
			context.append("!( ");
			this.convertOperand(expression.left(), context);
			context.append(" in ");
			this.convertOperand(expression.right(), context);
			context.append(" )");
		}
		else {
			this.convertOperand(expression.left(), context);
			context.append(getOperationSymbol(expression));
			this.convertOperand(expression.right(), context);
		}
	}

	private String getOperationSymbol(final Filter.Expression exp) {
		switch (exp.type()) {
			case AND:
				return " && ";
			case OR:
				return " || ";
			case EQ:
				return " == ";
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
				return " in ";
			default:
				throw new RuntimeException("Not supported expression type: " + exp.type());
		}
	}

	@Override
	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
		context.append("( ");
	}

	@Override
	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
		context.append(" )");
	}

	@Override
	protected void doKey(final Filter.Key key, final StringBuilder context) {
		context.append("@.").append(key.key());
	}

	@Override
	protected void doStartGroup(final Filter.Group group, final StringBuilder context) {
		context.append("(");
	}

	@Override
	protected void doEndGroup(final Filter.Group group, final StringBuilder context) {
		context.append(")");
	}

}
