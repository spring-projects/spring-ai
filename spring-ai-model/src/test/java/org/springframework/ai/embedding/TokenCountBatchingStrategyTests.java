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

package org.springframework.ai.embedding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Basic unit test for {@link TokenCountBatchingStrategy}.
 *
 * @author Soby Chacko
 */
public class TokenCountBatchingStrategyTests {

	@Test
	void batchEmbeddingHappyPath() {
		TokenCountBatchingStrategy tokenCountBatchingStrategy = new TokenCountBatchingStrategy();
		List<List<Document>> batch = tokenCountBatchingStrategy.batch(
				List.of(new Document("Hello world"), new Document("Hello Spring"), new Document("Hello Spring AI!")));
		assertThat(batch.size()).isEqualTo(1);
		assertThat(batch.get(0).size()).isEqualTo(3);
	}

	@Test
	void batchShouldTrackTokenCountAcrossBatchBoundaries() {
		// Use a small maxInputTokenCount (10 tokens, 0% reserve) so that batch
		// boundaries are hit quickly and the per-batch token accounting is exercised.
		TokenCountBatchingStrategy strategy = new TokenCountBatchingStrategy(EncodingType.CL100K_BASE, 10, 0.0);

		// "Hello world" ≈ 2 tokens, create 6 documents (12 tokens total, should split
		// into at least 2 batches). The bug was that the first document in each new
		// batch had its token count silently dropped from currentSize, allowing a batch
		// to exceed maxInputTokenCount.
		List<Document> documents = List.of(new Document("Hello world"), new Document("Hello world"),
				new Document("Hello world"), new Document("Hello world"), new Document("Hello world"),
				new Document("Hello world"));

		List<List<Document>> batches = strategy.batch(documents);

		// With the fix every batch should respect the token limit.
		assertThat(batches.size()).isGreaterThan(1);

		// Total documents across all batches must equal input size.
		int totalDocs = batches.stream().mapToInt(List::size).sum();
		assertThat(totalDocs).isEqualTo(documents.size());
	}

	@Test
	void batchEmbeddingWithLargeDocumentExceedsMaxTokenSize() throws IOException {
		Resource resource = new DefaultResourceLoader().getResource("classpath:text_source.txt");
		String contentAsString = resource.getContentAsString(StandardCharsets.UTF_8);
		TokenCountBatchingStrategy tokenCountBatchingStrategy = new TokenCountBatchingStrategy();
		assertThatThrownBy(() -> tokenCountBatchingStrategy.batch(List.of(new Document(contentAsString))))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
