package org.springframework.ai.autoconfigure.vectorstore.hanadb;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.HanaCloudVectorStore;
import org.springframework.ai.vectorstore.HanaCloudVectorStoreConfig;
import org.springframework.ai.vectorstore.HanaVectorEntity;
import org.springframework.ai.vectorstore.HanaVectorRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * @author Rahul Mittal
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({ HanaCloudVectorStore.class, DataSource.class, HanaVectorEntity.class })
@EnableConfigurationProperties(HanaCloudVectorStoreProperties.class)
public class HanaCloudVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public HanaCloudVectorStore vectorStore(HanaVectorRepository<? extends HanaVectorEntity> repository,
			EmbeddingClient embeddingClient, HanaCloudVectorStoreProperties properties) {

		return new HanaCloudVectorStore(repository, embeddingClient,
				HanaCloudVectorStoreConfig.builder()
					.tableName(properties.getTableName())
					.topK(properties.getTopK())
					.build());
	}

}