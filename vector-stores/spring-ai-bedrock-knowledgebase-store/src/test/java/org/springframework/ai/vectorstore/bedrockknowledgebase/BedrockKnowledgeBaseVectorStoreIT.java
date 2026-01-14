/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.bedrockknowledgebase;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.SearchType;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link BedrockKnowledgeBaseVectorStore}.
 *
 * <p>
 * These tests require:
 * <ul>
 * <li>AWS credentials configured (via environment or default credential chain)</li>
 * <li>BEDROCK_KB_ID environment variable set to a valid Knowledge Base ID</li>
 * <li>AWS_REGION environment variable (defaults to us-east-1)</li>
 * </ul>
 *
 * <p>
 * Note: Unlike other VectorStore implementations, Bedrock Knowledge Base is read-only.
 * Documents are managed through the Knowledge Base's data source sync process, not
 * through the VectorStore API. Therefore, these tests only verify search functionality
 * against pre-existing data in the Knowledge Base.
 *
 * @author Yuriy Bezsonov
 */
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "BEDROCK_KB_ID", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+") })
class BedrockKnowledgeBaseVectorStoreIT {

	private static String knowledgeBaseId;

	private static String awsRegion;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeAll
	static void beforeAll() {
		knowledgeBaseId = System.getenv("BEDROCK_KB_ID");
		awsRegion = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
	}

	@Test
	void shouldPerformSimilaritySearch() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			// Search with low threshold to ensure we get results
			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("policy").topK(5).similarityThreshold(0.0).build());

			// Verify response structure - KB should have documents
			assertThat(results).isNotEmpty();
			Document firstResult = results.get(0);
			assertThat(firstResult.getId()).isNotNull();
			assertThat(firstResult.getText()).isNotNull();
			assertThat(firstResult.getScore()).isNotNull();
			assertThat(firstResult.getScore()).isBetween(0.0, 1.0);
		});
	}

	@Test
	void shouldRespectTopKParameter() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("document").topK(2).similarityThresholdAll().build());

			assertThat(results).hasSizeLessThanOrEqualTo(2);
		});
	}

	@Test
	void shouldRespectSimilarityThreshold() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			// High threshold should return fewer or no results
			List<Document> highThresholdResults = vectorStore.similaritySearch(
					SearchRequest.builder().query("test query").topK(10).similarityThreshold(0.99).build());

			// Low threshold should return more results
			List<Document> lowThresholdResults = vectorStore.similaritySearch(
					SearchRequest.builder().query("test query").topK(10).similarityThreshold(0.01).build());

			// High threshold results should be subset of low threshold
			assertThat(highThresholdResults.size()).isLessThanOrEqualTo(lowThresholdResults.size());

			// All high threshold results should have score >= 0.99
			highThresholdResults.forEach(doc -> assertThat(doc.getScore()).isGreaterThanOrEqualTo(0.99));
		});
	}

	@Test
	void shouldReturnDocumentMetadata() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("travel").topK(5).similarityThreshold(0.0).build());

			// KB should have documents
			assertThat(results).isNotEmpty();
			Document doc = results.get(0);
			// Should have distance metadata (1 - score)
			assertThat(doc.getMetadata()).containsKey("distance");
		});
	}

	@Test
	void shouldProvideNativeClient() {
		this.contextRunner.run(context -> {
			BedrockKnowledgeBaseVectorStore vectorStore = context.getBean(BedrockKnowledgeBaseVectorStore.class);

			assertThat(vectorStore.getNativeClient()).isPresent();
			assertThat(vectorStore.getNativeClient().get()).isInstanceOf(BedrockAgentRuntimeClient.class);
		});
	}

	@Test
	void shouldReturnKnowledgeBaseId() {
		this.contextRunner.run(context -> {
			BedrockKnowledgeBaseVectorStore vectorStore = context.getBean(BedrockKnowledgeBaseVectorStore.class);

			assertThat(vectorStore.getKnowledgeBaseId()).isEqualTo(knowledgeBaseId);
		});
	}

	@Test
	void addShouldThrowUnsupportedOperationException() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			assertThatThrownBy(() -> vectorStore.add(List.of())).isInstanceOf(UnsupportedOperationException.class);
		});
	}

	@Test
	void deleteShouldThrowUnsupportedOperationException() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			assertThatThrownBy(() -> vectorStore.delete(List.of("id")))
				.isInstanceOf(UnsupportedOperationException.class);
		});
	}

	@Test
	void shouldRejectEmptyQuery() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			assertThatThrownBy(() -> vectorStore.similaritySearch(SearchRequest.builder().query("").build()))
				.isInstanceOf(IllegalArgumentException.class);
		});
	}

	@Test
	void shouldSearchWithSemanticSearchType() {
		new ApplicationContextRunner().withUserConfiguration(SemanticSearchTestApplication.class).run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("travel policy").topK(3).similarityThreshold(0.0).build());

			assertThat(results).isNotEmpty();
		});
	}

	@Test
	void shouldSearchWithHybridSearchType() {
		// Note: HYBRID search may not be supported by all KB configurations
		// This test verifies the configuration is passed correctly
		new ApplicationContextRunner().withUserConfiguration(HybridSearchTestApplication.class).run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			try {
				List<Document> results = vectorStore.similaritySearch(
						SearchRequest.builder().query("expense report").topK(3).similarityThreshold(0.0).build());
				// If HYBRID is supported, verify results
				assertThat(results).isNotNull();
			}
			catch (Exception e) {
				// HYBRID may not be supported - verify it's the expected error
				assertThat(e.getMessage()).containsIgnoringCase("HYBRID");
			}
		});
	}

	@Test
	void shouldSearchWithFilterExpression() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			// Search with a filter - even if no results match, should not throw
			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("policy")
				.topK(5)
				.similarityThreshold(0.0)
				.filterExpression("category == 'travel'")
				.build());

			// Filter may return empty if no matching metadata, but should not throw
			assertThat(results).isNotNull();
		});
	}

	@SpringBootConfiguration
	static class TestApplication {

		@Bean
		BedrockAgentRuntimeClient bedrockAgentRuntimeClient() {
			return BedrockAgentRuntimeClient.builder()
				.region(Region.of(awsRegion))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
		}

		@Bean
		BedrockKnowledgeBaseVectorStore vectorStore(BedrockAgentRuntimeClient client) {
			return BedrockKnowledgeBaseVectorStore.builder(client, knowledgeBaseId)
				.topK(10)
				.similarityThreshold(0.0)
				.build();
		}

	}

	@SpringBootConfiguration
	static class SemanticSearchTestApplication {

		@Bean
		BedrockAgentRuntimeClient bedrockAgentRuntimeClient() {
			return BedrockAgentRuntimeClient.builder()
				.region(Region.of(awsRegion))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
		}

		@Bean
		BedrockKnowledgeBaseVectorStore vectorStore(BedrockAgentRuntimeClient client) {
			return BedrockKnowledgeBaseVectorStore.builder(client, knowledgeBaseId)
				.searchType(SearchType.SEMANTIC)
				.build();
		}

	}

	@SpringBootConfiguration
	static class HybridSearchTestApplication {

		@Bean
		BedrockAgentRuntimeClient bedrockAgentRuntimeClient() {
			return BedrockAgentRuntimeClient.builder()
				.region(Region.of(awsRegion))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
		}

		@Bean
		BedrockKnowledgeBaseVectorStore vectorStore(BedrockAgentRuntimeClient client) {
			return BedrockKnowledgeBaseVectorStore.builder(client, knowledgeBaseId)
				.searchType(SearchType.HYBRID)
				.build();
		}

	}

}
