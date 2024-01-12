package org.springframework.ai.autoconfigure.postgresml;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.postgresml.PostgresMlEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Utkarsh Srivastava
 */
@AutoConfiguration
@ConditionalOnClass(PostgresMlEmbeddingClient.class)
@EnableConfigurationProperties(PostgresMlProperties.class)
public class PostgresMlAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public EmbeddingClient postgresMlEmbeddingClient(JdbcTemplate jdbcTemplate,
			PostgresMlProperties postgresMlProperties) {
		return new PostgresMlEmbeddingClient(jdbcTemplate, postgresMlProperties.getEmbedding().getTransformer(),
				postgresMlProperties.getEmbedding().getVectorType(), postgresMlProperties.getEmbedding().getKwargs(),
				postgresMlProperties.getEmbedding().getMetadataMode());
	}

}
