
package org.springframework.ai.vectorstore;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * Converts {@link org.springframework.ai.vectorstore.filter.Filter.Expression} into
 * Cosmos DB NoSQL API where clauses.
 */
class CosmosDBFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private Map<String, String> metadataFields;

	public CosmosDBFilterExpressionConverter(Collection<String> columns) {
		this.metadataFields = columns.stream().collect(Collectors.toMap(Function.identity(), Function.identity()));
	}

	/**
	 * Gets the metadata field from the Cosmos DB document.
	 * @param name The name of the metadata field.
	 * @return The name of the metadata field as it should appear in the query.
	 */
	private Optional<String> getMetadataField(String name) {
		String metadataField = name;
		return Optional.ofNullable(metadataFields.get(metadataField));
	}

	@Override
	protected void doKey(Key key, StringBuilder context) {
		String keyName = key.key();
		Optional<String> metadataField = getMetadataField(keyName);
		if (metadataField.isPresent()) {
			context.append("c.metadata." + metadataField.get());
		}
		else {
			throw new IllegalArgumentException(String.format("No metadata field %s has been configured", keyName));
		}
	}

	@Override
	protected void doExpression(Filter.Expression expression, StringBuilder context) {
		// Handling AND/OR
		if (AND.equals(expression.type()) || OR.equals(expression.type())) {
			doCompoundExpressionType(expression, context);
		}
		else {
			doSingleExpressionType(expression, context);
		}
	}

	private void doCompoundExpressionType(Filter.Expression expression, StringBuilder context) {
		context.append(" (");
		this.convertOperand(expression.left(), context);
		context.append(getOperationSymbol(expression));
		context.append(" (");
		this.convertOperand(expression.right(), context);
		int start = context.indexOf("[");
		if (start != -1) {
			context.replace(start, start + 1, "");
		}
		int end = context.indexOf("]");
		if (end != -1) {
			context.replace(end, end + 1, "");
		}
		context.append(")");
		context.append(")");
	}

	private void doSingleExpressionType(Filter.Expression expression, StringBuilder context) {
		this.convertOperand(expression.left(), context);
		context.append(getOperationSymbol(expression));
		context.append(" (");
		this.convertOperand(expression.right(), context);
		int start = context.indexOf("[");
		if (start != -1) {
			context.replace(start, start + 1, "");
		}
		int end = context.indexOf("]");
		if (end != -1) {
			context.replace(end, end + 1, "");
		}
		context.append(")");
	}

	private String getOperationSymbol(Filter.Expression exp) {
		switch (exp.type()) {
			case AND:
				return " AND ";
			case OR:
				return " OR ";
			case EQ:
				return " = ";
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
				return " IN ";
			case NIN:
				return " !IN ";
			default:
				throw new RuntimeException("Not supported expression type:" + exp.type());
		}
	}

	private void doBinaryOperation(String operator, Filter.Expression expression, StringBuilder context) {
		context.append("(");
		convertOperand(expression.left(), context);
		context.append(operator);
		convertOperand(expression.right(), context);
		context.append(")");
	}

	protected void doField(Filter.Expression expression, StringBuilder context) {
		// Assuming the left operand is the key in the expression
		if (expression.left() instanceof Filter.Key) {
			doKey((Filter.Key) expression.left(), context);
		}
		else {
			throw new UnsupportedOperationException("Expected a key in the left operand of the expression.");
		}
		doOperand(expression.type(), context);
		doValue((Filter.Value) expression.right(), context); // Cast to Filter.Value as
																// needed
	}

	private void doOperand(ExpressionType type, StringBuilder context) {
		switch (type) {
			case EQ -> context.append(" = ");
			case NE -> context.append(" != ");
			case GT -> context.append(" > ");
			case GTE -> context.append(" >= ");
			case IN -> context.append(" IN ");
			default -> throw new UnsupportedOperationException(String.format("Operator %s not supported", type));
		}
	}

}
