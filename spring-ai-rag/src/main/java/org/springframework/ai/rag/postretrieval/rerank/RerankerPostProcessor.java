package org.springframework.ai.rag.postretrieval.rerank;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

/**
 * The only supported entrypoint for rerank functionality in Spring AI RAG. This component
 * delegates reranking logic to CohereReranker, using the provided API key.
 *
 * This class is registered as a DocumentPostProcessor bean only if
 * spring.ai.rerank.enabled=true is set in the application properties.
 *
 * @author KoreaNirsa
 */
public class RerankerPostProcessor implements DocumentPostProcessor {

	private final CohereReranker reranker;

	RerankerPostProcessor(CohereApi cohereApi) {
		this.reranker = new CohereReranker(cohereApi);
	}

	/**
	 * Processes the retrieved documents by applying semantic reranking using the Cohere
	 * API
	 * @param query the user's input query
	 * @param documents the list of documents to be reranked
	 * @return a list of documents sorted by relevance score
	 */
	@Override
	public List<Document> process(Query query, List<Document> documents) {
		int topN = extractTopN(query);
		return reranker.rerank(query.text(), documents, topN);
	}

	/**
	 * Extracts the top-N value from the query context. If not present or invalid, it
	 * defaults to 3
	 * @param query the query containing optional context parameters
	 * @return the number of top documents to return
	 */
	private int extractTopN(Query query) {
		Object value = query.context().get("topN");
		return (value instanceof Number num) ? num.intValue() : 3;
	}

}
