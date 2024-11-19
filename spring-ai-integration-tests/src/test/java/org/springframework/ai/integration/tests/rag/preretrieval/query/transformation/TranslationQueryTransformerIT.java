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

package org.springframework.ai.integration.tests.rag.preretrieval.query.transformation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TranslationQueryTransformer}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class TranslationQueryTransformerIT {

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Test
	void whenTransformerWithDefaults() {
		Query query = new Query("Hvad er Danmarks hovedstad?");
		QueryTransformer queryTransformer = TranslationQueryTransformer.builder()
			.chatClientBuilder(ChatClient.builder(this.openAiChatModel))
			.targetLanguage("english")
			.build();

		Query transformedQuery = queryTransformer.apply(query);

		assertThat(transformedQuery).isNotNull();
		System.out.println(transformedQuery);
		assertThat(transformedQuery.text()).containsIgnoringCase("Denmark").containsIgnoringCase("capital");
	}

}
