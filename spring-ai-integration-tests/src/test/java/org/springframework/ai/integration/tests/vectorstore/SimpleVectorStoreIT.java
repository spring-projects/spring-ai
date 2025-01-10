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

package org.springframework.ai.integration.tests.vectorstore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SimpleVectorStore}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class SimpleVectorStoreIT {

	@Autowired
	private SimpleVectorStore vectorStore;

	List<Document> documents = List.of(
			Document.builder()
				.id("471a8c78-549a-4b2c-bce5-ef3ae6579be3")
				.text(getText("classpath:/test/data/spring.ai.txt"))
				.metadata(Map.of("meta1", "meta1"))
				.build(),
			Document.builder()
				.id("bc51d7f7-627b-4ba6-adf4-f0bcd1998f8f")
				.text(getText("classpath:/test/data/time.shelter.txt"))
				.metadata(Map.of())
				.build(),
			Document.builder()
				.id("d0237682-1150-44ff-b4d2-1be9b1731ee5")
				.text(getText("classpath:/test/data/great.depression.txt"))
				.metadata(Map.of("meta2", "meta2"))
				.build());

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@AfterEach
	void setUp() {
		vectorStore.delete(this.documents.stream().map(Document::getId).toList());
	}

	@Test
	public void searchWithThreshold() {
		Document document = Document.builder()
			.id(UUID.randomUUID().toString())
			.text("Spring AI rocks!!")
			.metadata("meta1", "meta1")
			.build();

		vectorStore.add(List.of(document));

		List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

		assertThat(results).hasSize(1);
		Document resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(document.getId());
		assertThat(resultDoc.getText()).isEqualTo("Spring AI rocks!!");
		assertThat(resultDoc.getMetadata()).containsKey("meta1");
		assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

		Document sameIdDocument = Document.builder()
			.id(document.getId())
			.text("The World is Big and Salvation Lurks Around the Corner")
			.metadata("meta2", "meta2")
			.build();

		vectorStore.add(List.of(sameIdDocument));

		results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

		assertThat(results).hasSize(1);
		resultDoc = results.get(0);
		assertThat(resultDoc.getId()).isEqualTo(document.getId());
		assertThat(resultDoc.getText()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
		assertThat(resultDoc.getMetadata()).containsKey("meta2");
		assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

		vectorStore.delete(List.of(document.getId()));
	}

}
