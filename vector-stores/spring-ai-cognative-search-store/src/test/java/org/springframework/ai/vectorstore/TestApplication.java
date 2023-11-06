package org.springframework.ai.vectorstore;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;

@SpringBootApplication
public class TestApplication {

	/*
	 * Crate an index client to build and destroy the test index
	 */
	@Bean
	public SearchIndexClient searchIndexClient(AzureSearchClientProperties props) {

		return new SearchIndexClientBuilder().endpoint(props.getEndpoint())
			.credential(new AzureKeyCredential(props.getApiKey()))
			.buildClient();
	}

	/*
	 * Create the Azure search client used internally by the vector store
	 */
	@Bean
	public SearchClient searchClient(AzureSearchClientProperties props) {

		return new SearchClientBuilder().endpoint(props.getEndpoint())
			.credential(new AzureKeyCredential(props.getApiKey()))
			.indexName(props.getIndex())
			.buildClient();
	}

	/*
	 * Create the vector store using an Azure search client that is connected to a
	 * Cognitive Search Index
	 */
	@Bean
	public VectorStore vectorStore(SearchClient searchClient, EmbeddingClient embeddingClient) {
		return new AzureCognitiveSearchVectorStore(searchClient, embeddingClient);
	}

}
