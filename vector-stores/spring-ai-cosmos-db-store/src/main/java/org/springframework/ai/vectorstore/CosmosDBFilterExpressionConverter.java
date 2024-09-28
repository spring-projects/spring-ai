
package org.springframework.ai.vectorstore;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Converts {@link org.springframework.ai.vectorstore.filter.Filter.Expression} into Cosmos DB SQL API
 * where clauses.
 */
class CosmosDBFilterExpressionConverter extends AbstractFilterExpressionConverter {

    private final Map<String, String> columnsByName;

    public CosmosDBFilterExpressionConverter(Collection<String> columns) {
        this.columnsByName = columns.stream()
            .collect(Collectors.toMap(Function.identity(), Function.identity()));
    }

    /**
     * Gets the metadata field from the Cosmos DB document.
     * @param name The name of the metadata field.
     * @return The name of the metadata field as it should appear in the query.
     */
    private Optional<String> getMetadataField(String name) {
        // Assume all metadata fields are stored under "metadata" in the JSON document
        String metadataField = "metadata." + name;
        return Optional.ofNullable(columnsByName.get(metadataField));
    }

    @Override
    protected void doKey(Key key, StringBuilder context) {
        String keyName = key.key();
        Optional<String> metadataField = getMetadataField(keyName);
        if (metadataField.isPresent()) {
            context.append(metadataField.get());
        } else {
            throw new IllegalArgumentException(String.format("No metadata field %s has been configured", keyName));
        }
    }

    @Override
    protected void doExpression(Filter.Expression expression, StringBuilder context) {
        switch (expression.type()) {
            case AND -> doBinaryOperation(" AND ", expression, context);
            case OR -> doBinaryOperation(" OR ", expression, context);
            case NIN, NOT -> throw new UnsupportedOperationException(
                    String.format("Expression type %s not yet implemented. Patches welcome.", expression.type()));
            default -> doField(expression, context);
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
		} else {
			throw new UnsupportedOperationException("Expected a key in the left operand of the expression.");
		}
		doOperand(expression.type(), context);
		doValue((Filter.Value) expression.right(), context); // Cast to Filter.Value as needed
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

    @Override
    protected void doValue(Value value, StringBuilder context) {
        if (value.value() instanceof Collection) {
            context.append(value.value().toString());
        } else {
            context.append("'").append(value.value().toString()).append("'");
        }
    }
}
