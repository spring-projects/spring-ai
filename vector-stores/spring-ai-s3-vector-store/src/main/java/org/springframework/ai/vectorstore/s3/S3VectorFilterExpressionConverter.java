package org.springframework.ai.vectorstore.s3;

import org.springframework.ai.vectorstore.filter.Filter;
import software.amazon.awssdk.core.document.Document;


/**
 * @author Matej Nedic
 */
public interface S3VectorFilterExpressionConverter {

	/**
	 * Convert the given {@link Filter.Expression} into a {@link Document} representation.
	 * @param expression the expression to convert
	 * @return the converted expression
	 */
	Document convertExpression(Filter.Expression expression);
}
