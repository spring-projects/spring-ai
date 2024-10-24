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

package org.springframework.ai.watsonx.api;

import org.junit.Test;

import org.springframework.ai.watsonx.WatsonxAiEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pablo Sanchidrian Herrera
 */
public class WatsonxAiEmbeddingOptionTest {

	@Test
	public void testWithModel() {
		WatsonxAiEmbeddingOptions options = new WatsonxAiEmbeddingOptions();
		options.withModel("test-model");
		assertThat("test-model").isEqualTo(options.getModel());
	}

	@Test
	public void testCreateFactoryMethod() {
		WatsonxAiEmbeddingOptions options = WatsonxAiEmbeddingOptions.create();
		assertThat(options).isNotNull();
		assertThat(options.getModel()).isNull();
	}

	@Test
	public void testFromOptionsFactoryMethod() {
		WatsonxAiEmbeddingOptions originalOptions = new WatsonxAiEmbeddingOptions().withModel("original-model");
		WatsonxAiEmbeddingOptions newOptions = WatsonxAiEmbeddingOptions.fromOptions(originalOptions);

		assertThat(newOptions).isNotNull();
		assertThat("original-model").isEqualTo(newOptions.getModel());
	}

}
