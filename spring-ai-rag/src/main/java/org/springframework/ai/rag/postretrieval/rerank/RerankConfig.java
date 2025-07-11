package org.springframework.ai.rag.postretrieval.rerank;

import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rerank configuration that conditionally registers a DocumentPostProcessor
 * when rerank is enabled via application properties.
 *
 * This configuration is activated only when the following properties are set
 * 
 * <ul>
 *   <li>spring.ai.rerank.enabled=true</li>
 *   <li>spring.ai.rerank.cohere.api-key=your-api-key</li>
 * </ul>
 *
 * @author KoreaNirsa
 */
@Configuration
public class RerankConfig {
    @Value("${spring.ai.rerank.cohere.api-key}")
    private String apiKey;
    
    @Bean
    @ConditionalOnProperty(name = "spring.ai.rerank.enabled", havingValue = "true")
    public DocumentPostProcessor rerankerPostProcessor() {
        return new RerankerPostProcessor(CohereApi.builder().apiKey(apiKey).build());
    }
}