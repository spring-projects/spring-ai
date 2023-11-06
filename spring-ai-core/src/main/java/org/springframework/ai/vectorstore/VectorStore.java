package org.springframework.ai.vectorstore;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.ai.vectorstore.filter.converter.PrintFilterExpressionConverter;

public interface VectorStore extends DocumentWriter {

	/**
	 * Adds Documents to the vector store.
	 * @param documents the list of documents to store Will throw an exception if the
	 * underlying provider checks for duplicate IDs on add
	 */
	void add(List<Document> documents);

	default void accept(List<Document> documents) {
		add(documents);
	}

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
	 * Retrieve documents with similar to the query content. You can limit your vector
	 * search based on {@link Document} metadata. Spring AI lets you attach metadata
	 * key-value pairs to Documents stored in teh {@link VectorStore}, and specify filter
	 * expressions when you query the them.
	 *
	 * Searches with metadata filters retrieve exactly the number of nearest-neighbor
	 * results that match the filters. For most cases, the search latency will be even
	 * lower than unfiltered searches.
	 *
	 * NOTE: This filter expressions have different syntax for each Vector Store, making
	 * this method not portable across different {@link VectorStore} implementations. For
	 * portable implementations use
	 * {@link VectorStore#similaritySearch(String, int, double, org.springframework.ai.vectorstore.filter.Filter.Expression)}
	 * instead. The {@link Filter.Expression} is portable across most Spring AI vector
	 * store implementation. in combination with {@link FilterExpressionTextParser} or
	 * {@link FilterExpressionBuilder}.
	 * @param query The query to send, it will be converted to an embedding based on the
	 * configuration of the vector store.
	 * @param k the top 'k' similar results
	 * @param threshold the lower bound of the similarity score
	 * @param vectorSpecificFilterExpression vector store specific metadata filters.
	 * @return similar documents that match the requested threshold and filter.
	 */
	default List<Document> similaritySearch(String query, int k, double threshold,
			String vectorSpecificFilterExpression) {
		throw new UnsupportedOperationException("This vector store doesn't support search filtering");
	}

	/**
	 * Retrieve documents with similar to the query content. You can limit your vector
	 * search based on {@link Document} metadata. Spring AI lets you attach metadata
	 * key-value pairs to Documents stored in teh {@link VectorStore}, and specify filter
	 * expressions when you query the them.
	 *
	 * Searches with metadata filters retrieve exactly the number of nearest-neighbor
	 * results that match the filters. For most cases, the search latency will be even
	 * lower than unfiltered searches.
	 *
	 * The {@link Filter.Expression} is portable across most Spring AI vector store
	 * implementation. The {@link FilterExpressionBuilder} or
	 * {@link FilterExpressionTextParser} provide common text and DSL filter definition.
	 * @param query The query to send, it will be converted to an embedding based on the
	 * configuration of the vector store.
	 * @param k the top 'k' similar results
	 * @param threshold the lower bound of the similarity score
	 * @param filterExpression portable metadata filter predicates.
	 * @return similar documents that match the requested threshold and filter.
	 */
	default List<Document> similaritySearch(String query, int k, double threshold, Filter.Expression filterExpression) {
		throw new UnsupportedOperationException("This vector store doesn't support search filtering");
	}

}
