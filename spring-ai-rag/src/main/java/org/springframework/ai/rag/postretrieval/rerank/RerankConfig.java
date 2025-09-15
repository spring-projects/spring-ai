package org.springframework.ai.rag.postretrieval.rerank;

import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rerank configuration that conditionally registers a DocumentPostProcessor when rerank
 * is enabled via application properties.
 *
 * This configuration is activated only when the following properties are set
 *
 * <ul>
 * <li>spring.ai.rerank.enabled=true</li>
 * <li>spring.ai.rerank.cohere.api-key=your-api-key</li>
 * </ul>
 *
 * @author KoreaNirsa
 */
@Configuration
public class RerankConfig {

	@Value("${spring.ai.rerank.cohere.api-key}")
	private String apiKey;

	/**
	 * Registers a DocumentPostProcessor bean that enables reranking using Cohere.
	 *
	 * This bean is only created when the property `spring.ai.rerank.enabled=true` is set.
	 * The API key is injected from application properties or environment variables.
	 * @return An instance of RerankerPostProcessor backed by Cohere API
	 */
	@Bean
	@ConditionalOnProperty(name = "spring.ai.rerank.enabled", havingValue = "true")
	public DocumentPostProcessor rerankerPostProcessor() {
		return new RerankerPostProcessor(CohereApi.builder().apiKey(apiKey).build());
	}

	/**
	 * Provides a fallback DocumentPostProcessor when reranking is disabled or no custom
	 * implementation is registered.
	 *
	 * This implementation performs no reranking and simply returns the original list of
	 * documents. If additional post-processing is required, a custom bean should be
	 * defined.
	 * @return A pass-through DocumentPostProcessor that returns input as-is
	 */
	@Bean
	@ConditionalOnMissingBean
	public DocumentPostProcessor noOpPostProcessor() {
		return (query, documents) -> documents;
	}

}