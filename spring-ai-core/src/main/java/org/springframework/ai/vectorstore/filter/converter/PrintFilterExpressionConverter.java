package org.springframework.ai.vectorstore.filter.converter;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;

//
// Test, print walker
//
public class PrintFilterExpressionConverter extends AbstractFilterExpressionConverter {

	public void doExpression(Expression expression, StringBuilder context) {
		this.convert(expression.left(), context);
		context.append(" " + expression.type() + " ");
		this.convert(expression.right(), context);

	}

	public void doKey(Key key, StringBuilder context) {
		context.append(key.key());
	}

	public void doStartGroup(Group group, StringBuilder context) {
		context.append("(");
	}

	public void doEndGroup(Group group, StringBuilder context) {
		context.append(")");
	}

}