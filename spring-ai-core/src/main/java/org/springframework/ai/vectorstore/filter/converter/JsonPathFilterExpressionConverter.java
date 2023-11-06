package org.springframework.ai.vectorstore.filter.converter;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;

//
// PgVectorStore walker
// (https://www.postgresql.org/docs/current/functions-json.html)
//
public class JsonPathFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		this.convert(expression.left(), context);
		context.append(getOperationSymbol(expression));
		this.convert(expression.right(), context);
	}

	private String getOperationSymbol(Expression exp) {
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
			case NIN:
				return " nin ";
			default:
				throw new RuntimeException("Not supported expression type: " + exp.type());
		}
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		context.append("$." + key.key());
	}

	@Override
	protected void doStartGroup(Group group, StringBuilder context) {
		context.append("(");
	}

	@Override
	protected void doEndGroup(Group group, StringBuilder context) {
		context.append(")");
	}

}