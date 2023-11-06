package org.springframework.ai.vectorstore.filter.converter;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;

//
// Pinecone Vector Store walkder
// (https://docs.pinecone.io/docs/metadata-filtering)
//
public class PineconeFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected void doExpression(Expression exp, StringBuilder context) {

		context.append("{");
		if (exp.type() == ExpressionType.AND || exp.type() == ExpressionType.OR) {
			context.append(getOperationSymbol(exp));
			context.append("[");
			this.convert(exp.left(), context);
			context.append(",");
			this.convert(exp.right(), context);
			context.append("]");
		}
		else {
			this.convert(exp.left(), context);
			context.append("{");
			context.append(getOperationSymbol(exp));
			this.convert(exp.right(), context);
			context.append("}");
		}
		context.append("}");

	}

	private String getOperationSymbol(Expression exp) {
		return "\"$" + exp.type().toString().toLowerCase() + "\": ";
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		context.append("\"" + key.key() + "\": ");
	}

}