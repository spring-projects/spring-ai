package org.springframework.ai.vectorstore.filter.converter;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;

//
// Milvus vector store walker
// (https://milvus.io/docs/json_data_type.md)
//
public class MilvusFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected void doExpression(Expression exp, StringBuilder context) {
		this.convert(exp.left(), context);
		context.append(getOperationSymbol(exp));
		this.convert(exp.right(), context);
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
				throw new RuntimeException("Not supported expression type:" + exp.type());
		}
	}

	@Override
	protected void doGroup(Group group, StringBuilder context) {
		this.convert(new Expression(ExpressionType.AND, group.content(), group.content()), context); // trick
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		context.append("metadata[\"" + key.key() + "\"]");
	}

}