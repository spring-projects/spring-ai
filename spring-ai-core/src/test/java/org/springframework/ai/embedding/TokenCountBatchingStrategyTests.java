/*
 * Copyright 2023-2024 the original author or authors.
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
	void batchEmbeddingWithLargeDocumentExceedsMaxTokenSize() throws IOException {
		Resource resource = new DefaultResourceLoader().getResource("classpath:text_source.txt");
		String contentAsString = resource.getContentAsString(StandardCharsets.UTF_8);
		TokenCountBatchingStrategy tokenCountBatchingStrategy = new TokenCountBatchingStrategy();
		assertThatThrownBy(() -> tokenCountBatchingStrategy.batch(List.of(new Document(contentAsString))))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
