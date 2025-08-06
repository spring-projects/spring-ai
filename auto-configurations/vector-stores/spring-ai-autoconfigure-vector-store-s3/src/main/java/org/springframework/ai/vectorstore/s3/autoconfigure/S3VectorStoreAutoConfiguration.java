package org.springframework.ai.vectorstore.s3.autoconfigure;


import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.s3.S3VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

/**
 * {@link AutoConfiguration Auto-configuration} for S3 Vector Store.
 *
 * @author Matej Nedic
 */
@AutoConfiguration
@ConditionalOnClass({S3VectorsClient.class, EmbeddingModel.class})
@EnableConfigurationProperties(S3VectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.S3,
		matchIfMissing = true)
public class S3VectorStoreAutoConfiguration {

	private final S3VectorStoreProperties properties;

	S3VectorStoreAutoConfiguration(S3VectorStoreProperties p) {
		Assert.notNull(p.getIndexName(), "Index name cannot be null!");
		Assert.notNull(p.getVectorBucketName(), "Bucket name cannot be null");
		this.properties = p;
	}
	@Bean
	@ConditionalOnMissingBean
	S3VectorStore s3VectorStore(S3VectorsClient s3VectorsClient, EmbeddingModel embeddingModel) {
		S3VectorStore.Builder builder = new S3VectorStore.Builder(s3VectorsClient, embeddingModel);
		builder.indexName(properties.getIndexName()).vectorBucketName(properties.getVectorBucketName());
		return builder.build();
	}
}
