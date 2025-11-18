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

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Basic unit tests for {@link TokenCountBatchingStrategy}.
 *
 * @author Soby Chacko
 * @author John Blum
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

	@Test
	void documentTokenCountExceedsConfiguredMaxTokenCount() {

		Document mockDocument = mock(Document.class);
		ContentFormatter mockContentFormatter = mock(ContentFormatter.class);
		TokenCountEstimator mockTokenCountEstimator = mock(TokenCountEstimator.class);

		doReturn("123abc").when(mockDocument).getId();
		doReturn(10).when(mockTokenCountEstimator).estimate(anyString());
		doReturn("test").when(mockDocument).getFormattedContent(any(), any());

		TokenCountBatchingStrategy batchingStrategy = new TokenCountBatchingStrategy(mockTokenCountEstimator, 9, 0.0d,
				mockContentFormatter, MetadataMode.EMBED);

		assertThatExceptionOfType(MaxTokenCountExceededException.class)
			.isThrownBy(() -> batchingStrategy.batch(List.of(mockDocument)))
			.withMessage(
					"Tokens [10] from Document [123abc] exceeds the configured maximum number of input tokens allowed [9]")
			.withNoCause();

		verify(mockDocument, times(1)).getId();
		verify(mockDocument, times(1)).getFormattedContent(eq(mockContentFormatter), eq(MetadataMode.EMBED));
		verify(mockTokenCountEstimator, times(1)).estimate(eq("test"));
		verifyNoMoreInteractions(mockDocument, mockTokenCountEstimator);
	}

}
