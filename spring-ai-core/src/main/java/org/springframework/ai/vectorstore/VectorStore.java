package org.springframework.ai.vectorstore;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

public interface VectorStore extends DocumentWriter {

	/**
	 * Adds list of {@link Document}s to the vector store.
	 * @param documents the list of documents to store. Throws an exception if the
	 * underlying provider checks for duplicate IDs.
	 */
	void add(List<Document> documents);

	@Override
	default void accept(List<Document> documents) {
		add(documents);
	}

	/**
	 * Deletes documents from the vector store.
	 * @param idList list of document ids for which documents will be removed.
	 * @return
	 */
	Optional<Boolean> delete(List<String> idList);

	List<Document> similaritySearch(String query);

	List<Document> similaritySearch(String query, int k);

	/**
	 * @param query The query to send, it will be converted to an embedding based on the
	 * configuration of the vector store.
	 * @param k the top 'k' similar results
	 * @param threshold the lower bound of the similarity score
	 * @return similar documents
	 */
	List<Document> similaritySearch(String query, int k, double threshold);

	/**
	 * Retrieves documents by query embedding similarity and metadata filters to retrieve
	 * exactly the number of nearest-neighbor results that match the filters.
	 *
	 * For example if your {@link Document#getMetadata()} has a schema like:
	 *
	 * <pre>{@code
	 * &#123;
	 *  "country":  <Text>,
	 *  "city":     <Text>,
	 *  "year":     <Number>,
	 *  "price":    <Decimal>,
	 *  "isActive": <Boolean>
	 * &#125;
	 * }</pre>
	 *
	 * then you can constrain the search result with metadata filter expressions
	 * equivalent to (country == 'UK' AND year >= 2020 AND isActive == true). You can
	 * build this filter programmatically like this:
	 *
	 * <pre>{@code
	 *
	 * new Filter.Expression(AND,
	 * 		new Expression(EQ, new Key("country"), new Value("UK")),
	 * 		new Expression(AND,
	 * 				new Expression(GTE, new Key("year"), new Value(2020)),
	 * 				new Expression(EQ, new Key("isActive"), new Value(true))));
	 *
	 * }</pre>
	 *
	 * and it will ensure that the response contains only embeddings that match the
	 * specified filer criteria. <br/>
	 *
	 * The {@link Filter.Expression} is portable across all vector stores that offer
	 * metadata filtering. The {@link FilterExpressionBuilder} is expression DSL and
	 * {@link FilterExpressionTextParser} is text expression parser that build
	 * {@link Filter.Expression}.
	 * @param topK the top 'k' similar results to return.
	 * @param similarityThreshold the lower bound of the similarity score
	 * @param filterExpression portable metadata filter expression.
	 * @return similar documents that match the requested similarity threshold and filter.
	 */
	default List<Document> similaritySearch(String query, int topK, double similarityThreshold,
			Filter.Expression filterExpression) {
		throw new UnsupportedOperationException("This vector store doesn't support search filtering");
	}

	/**
	 * Retrieves documents by query embedding similarity and metadata filters to retrieve
	 * exactly the number of nearest-neighbor results that match the filters.
	 *
	 * For example if your {@link Document#getMetadata()} has a schema like:
	 *
	 * <pre>{@code
	 * &#123;
	 *  "country":  <Text>,
	 *  "city":     <Text>,
	 *  "year":     <Number>,
	 *  "price":    <Decimal>,
	 *  "isActive": <Boolean>
	 * &#125;
	 * }</pre>
	 *
	 * then you can constrain the search result with metadata filter expressions like:
	 *
	 * <pre>{@code
	 *country == 'UK' && year >= 2020 && isActive == true
	 *                        Or
	 *country == 'BG' && (city NOT IN ['Sofia', 'Plovdiv'] || price < 134.34)
	 * }</pre>
	 *
	 * This ensures that the response contains only embeddings that match the specified
	 * filer criteria. <br/>
	 *
	 * The declarative, SQL like, filter syntax is portable across all vector stores
	 * supporting the filter search feature.<br/>
	 *
	 * The {@link FilterExpressionTextParser} is used to convert the text filter
	 * expression into {@link Filter.Expression}.
	 * @param topK the top 'k' similar results to return.
	 * @param similarityThreshold the lower bound of the similarity score
	 * @param filterExpression portable metadata filter expression.
	 * @return similar documents that match the requested similarity threshold and filter.
	 */
	default List<Document> similaritySearch(String query, int topK, double similarityThreshold,
			String filterExpression) {
		var filterExpressionObject = Filter.parser().parse(filterExpression);
		return similaritySearch(query, topK, similarityThreshold, filterExpressionObject);
	}

}
