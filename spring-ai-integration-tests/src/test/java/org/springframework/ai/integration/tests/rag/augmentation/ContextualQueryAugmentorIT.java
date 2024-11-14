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

package org.springframework.ai.integration.tests.rag.augmentation;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.augmentation.ContextualQueryAugmentor;
import org.springframework.ai.rag.augmentation.QueryAugmentor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ContextualQueryAugmentor}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class ContextualQueryAugmentorIT {

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Test
	void whenContextIsProvided() {
		QueryAugmentor queryAugmentor = ContextualQueryAugmentor.builder().build();
		Query query = new Query("What is Iorek's dream?");
		List<Document> documents = List
			.of(new Document("Iorek was a little polar bear who lived in the Arctic circle."), new Document(
					"Iorek loved to explore the snowy landscape and dreamt of one day going on an adventure around the North Pole."));

		Query augmentedQuery = queryAugmentor.augment(query, documents);
		String response = this.openAiChatModel.call(augmentedQuery.text());

		assertThat(response).isNotEmpty();
		System.out.println(response);
		assertThat(response).containsIgnoringCase("North Pole");
		assertThat(response).doesNotContainIgnoringCase("context");
		assertThat(response).doesNotContainIgnoringCase("information");
	}

	@Test
	void whenAllowEmptyContext() {
		QueryAugmentor queryAugmentor = ContextualQueryAugmentor.builder().build();
		Query query = new Query("What is Iorek's dream?");
		List<Document> documents = List.of();
		Query augmentedQuery = queryAugmentor.augment(query, documents);
		String response = this.openAiChatModel.call(augmentedQuery.text());

		assertThat(response).isNotEmpty();
		System.out.println(response);
		assertThat(response).containsIgnoringCase("Iorek");
	}

	@Test
	void whenNotAllowEmptyContext() {
		QueryAugmentor queryAugmentor = ContextualQueryAugmentor.builder().allowEmptyContext(false).build();
		Query query = new Query("What is Iorek's dream?");
		List<Document> documents = List.of();
		Query augmentedQuery = queryAugmentor.augment(query, documents);
		String response = this.openAiChatModel.call(augmentedQuery.text());

		assertThat(response).isNotEmpty();
		System.out.println(response);
		assertThat(response).doesNotContainIgnoringCase("Iorek");
	}

}
