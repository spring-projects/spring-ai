package org.springframework.ai.vectorstore.azure.autoconfigure;


import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.s3.S3VectorStore;
import org.springframework.ai.vectorstore.s3.autoconfigure.S3VectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Matej Nedic
 */
@ExtendWith(OutputCaptureExtension.class)
public class S3VectorStoreAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of( S3VectorStoreAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.withPropertyValues("spring.ai.vectorstore.s3.vectorBucketName=testBucket")
			.withPropertyValues("spring.ai.vectorstore.s3.indexName=testIndex");

	@Test
	public void autoConfigurationDisabledWhenTypeIsNone() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
			assertThat(context.getBeansOfType(S3VectorStore.class)).isEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
		});
	}

	@Test
	public void autoConfigurationEnabledByDefault() {
		this.contextRunner.run(context -> {
			assertThat(context.getBeansOfType(S3VectorStore.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(S3VectorStore.class);
		});
	}

	@Test
	public void autoConfigurationEnabledWhenTypeIsS3() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=S3").run(context -> {
			assertThat(context.getBeansOfType(S3VectorStore.class)).isNotEmpty();
			assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
			assertThat(context.getBean(VectorStore.class)).isInstanceOf(S3VectorStore.class);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public S3VectorsClient s3VectorsClient() {
			return S3VectorsClient.builder().region(Region.US_EAST_1).credentialsProvider(DefaultCredentialsProvider.builder().build()).build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}


}
