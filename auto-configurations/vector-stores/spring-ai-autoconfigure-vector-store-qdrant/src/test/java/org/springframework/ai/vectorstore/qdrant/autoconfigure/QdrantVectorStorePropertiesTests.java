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

package org.springframework.ai.vectorstore.qdrant.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 */
public class QdrantVectorStorePropertiesTests {

	@Test
	public void defaultValues() {
		var props = new QdrantVectorStoreProperties();

		assertThat(props.getCollectionName()).isEqualTo(QdrantVectorStore.DEFAULT_COLLECTION_NAME);
		assertThat(props.getContentFieldName()).isEqualTo(QdrantVectorStore.DEFAULT_CONTENT_FIELD_NAME);
		assertThat(props.getHost()).isEqualTo("localhost");
		assertThat(props.getPort()).isEqualTo(6334);
		assertThat(props.isUseTls()).isFalse();
		assertThat(props.getApiKey()).isNull();
	}

	@Test
	public void customValues() {
		var props = new QdrantVectorStoreProperties();

		props.setCollectionName("MY_COLLECTION");
		props.setContentFieldName("MY_CONTENT_FIELD");
		props.setHost("MY_HOST");
		props.setPort(999);
		props.setUseTls(true);
		props.setApiKey("MY_API_KEY");

		assertThat(props.getCollectionName()).isEqualTo("MY_COLLECTION");
		assertThat(props.getContentFieldName()).isEqualTo("MY_CONTENT_FIELD");
		assertThat(props.getHost()).isEqualTo("MY_HOST");
		assertThat(props.getPort()).isEqualTo(999);
		assertThat(props.isUseTls()).isTrue();
		assertThat(props.getApiKey()).isEqualTo("MY_API_KEY");
	}

}
