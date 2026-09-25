/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore.redis;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.RedisProtocol;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.test.vectorstore.AbstractVectorStoreUpsertTests;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Upsert verification for {@link RedisVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * Like {@link RedisVectorStoreIT}, this needs only Testcontainers Redis Stack and no
 * hosted embedding provider: {@code doUpsert} never embeds, and read-back only needs a
 * query vector, so a {@link FixedDimensionEmbeddingModel} is wired instead. Redis writes
 * a batch in a single pipeline, so {@code pairingAcrossBatches} exercises positional
 * pairing across all entries rather than across write-batch boundaries.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
public class RedisVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeEach
	void cleanDatabase() {
		this.contextRunner.run(context -> context.getBean(RedisVectorStore.class).getJedisClient().flushAll());
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Override
	protected int embeddingDimensions() {
		return EMBEDDING_DIMENSIONS;
	}

	@Override
	protected VectorStore createStoreOverExistingSchema(VectorStore schemaOwner, EmbeddingModel embeddingModel) {
		return RedisVectorStore.builder(((RedisVectorStore) schemaOwner).getJedisClient(), embeddingModel)
			.metadataFields(MetadataField.tag("tag"), MetadataField.tag(DocumentMetadata.CONTENT_REF.value()))
			.build();
	}

	@Override
	protected VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner, EmbeddingModel embeddingModel,
			int dimensions) {
		RedisVectorStore store = RedisVectorStore
			.builder(((RedisVectorStore) schemaOwner).getJedisClient(), embeddingModel)
			.indexName("upsert-configured-index")
			.prefix("upsert-configured:")
			.metadataFields(MetadataField.tag("tag"))
			.dimensions(dimensions)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();
		return store;
	}

	@Test
	void upsertIntoExistingIndexOverResp2() {
		executeTest(vectorStore -> {
			// Over RESP2, FT.INFO describes each field as a flat list rather than a map.
			RedisClient resp2Client = RedisClient.builder()
				.hostAndPort(redisContainer.getHost(), redisContainer.getFirstMappedPort())
				.clientConfig(DefaultJedisClientConfig.builder().protocol(RedisProtocol.RESP2).build())
				.build();
			try {
				CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS);
				RedisVectorStore writer = RedisVectorStore.builder(resp2Client, embeddingModel)
					.metadataFields(MetadataField.tag("tag"))
					.build();

				writer.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "resp2", Map.of("tag", "r"))));
				assertWrongDimensionRejected(writer);
				assertThat(embeddingModel.calls()).isZero();
			}
			finally {
				resp2Client.close();
			}
		});
	}

	@Test
	void upsertRefusedWhenVectorSizeUnknown() {
		executeTest(vectorStore -> {
			// No configured dimension, no index to read it from, and no reachable model:
			// Redis would store a vector it can never find, so the write is refused.
			RedisVectorStore writer = RedisVectorStore
				.builder(((RedisVectorStore) vectorStore).getJedisClient(), new UnreachableEmbeddingModel())
				.indexName("missing-index")
				.prefix("missing:")
				.build();

			assertThatIllegalStateException()
				.isThrownBy(() -> writer
					.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "unknown size", Map.of()))))
				.withMessageContaining("missing-index");
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public RedisVectorStore vectorStore(EmbeddingModel embeddingModel) {
			return RedisVectorStore
				.builder(RedisClient.builder()
					.hostAndPort(redisContainer.getHost(), redisContainer.getFirstMappedPort())
					.build(), embeddingModel)
				// Redis returns only the fields declared here, so both the suite's "tag"
				// and the content_ref pointer have to be declared to survive a read.
				.metadataFields(MetadataField.tag("tag"), MetadataField.tag(DocumentMetadata.CONTENT_REF.value()))
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

	}

	private static final class UnreachableEmbeddingModel implements EmbeddingModel {

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			throw new IllegalStateException("embedding model is unreachable");
		}

		@Override
		public float[] embed(Document document) {
			throw new IllegalStateException("embedding model is unreachable");
		}

		@Override
		public int dimensions() {
			throw new IllegalStateException("embedding model is unreachable");
		}

	}

}
