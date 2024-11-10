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
package org.springframework.ai.integration.tests.rag.retrieval.search;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;

/**
 * Integration tests for {@link VectorStoreDocumentRetriever}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class VectorStoreDocumentRetrieverIT {

	private static final Map<String, Document> documents = Map.of("1", new Document(
			"Anacletus was a majestic snowy owl with unusually bright golden eyes and distinctive black speckles across his wings.",
			Map.of("location", "Whispering Woods")), "2",
			new Document(
					"Anacletus made his home in an ancient hollow oak tree deep within the Whispering Woods, where local villagers often heard his haunting calls at midnight.",
					Map.of("location", "Whispering Woods")),
			"3",
			new Document(
					"Despite being a nocturnal hunter like other owls, Anacletus had developed a peculiar habit of collecting shiny objects, especially lost coins and jewelry that glinted in the moonlight.",
					Map.of()),
			"4",
			new Document(
					"Birba was a plump Siamese cat with mismatched eyes - one blue and one green - who spent her days lounging on velvet cushions and judging everyone with a perpetual look of disdain.",
					Map.of("location", "Alfea")));

	@Autowired
	PgVectorStore pgVectorStore;

	@BeforeEach
	void setUp() {
		pgVectorStore.add(List.copyOf(documents.values()));
	}

	@AfterEach
	void tearDown() {
		pgVectorStore.delete(documents.values().stream().map(Document::getId).toList());
	}

	@Test
	void withFilter() {
		DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
			.vectorStore(pgVectorStore)
			.similarityThreshold(0.50)
			.topK(3)
			.filterExpression(
					new Filter.Expression(EQ, new Filter.Key("location"), new Filter.Value("Whispering Woods")))
			.build();

		List<Document> retrievedDocuments = documentRetriever.retrieve(new Query("Who is Anacletus?"));

		assertThat(retrievedDocuments).hasSize(2);
		assertThat(retrievedDocuments).anyMatch(document -> document.getId().equals(documents.get("1").getId()));
		assertThat(retrievedDocuments).anyMatch(document -> document.getId().equals(documents.get("2").getId()));

		retrievedDocuments = documentRetriever.retrieve(new Query("Who is Birba?"));
		assertThat(retrievedDocuments).noneMatch(document -> document.getId().equals(documents.get("4").getId()));
	}

	@Test
	void withNoFilter() {
		DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
			.vectorStore(pgVectorStore)
			.similarityThreshold(0.50)
			.topK(3)
			.build();

		List<Document> retrievedDocuments = documentRetriever.retrieve(new Query("Who is Anacletus?"));

		assertThat(retrievedDocuments).hasSize(3);
		assertThat(retrievedDocuments).anyMatch(document -> document.getId().equals(documents.get("1").getId()));
		assertThat(retrievedDocuments).anyMatch(document -> document.getId().equals(documents.get("2").getId()));
		assertThat(retrievedDocuments).anyMatch(document -> document.getId().equals(documents.get("3").getId()));
	}

}
