/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.vectorstore.gemfire;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.GemFireVectorStore;

/**
 * @author Philipp Kessler
 */
class GemFireVectorStorePropertiesTests {

	@Test
	void defaultValues() {
		var props = new GemFireVectorStoreProperties();
		assertThat(props.getHost()).isNull();
		assertThat(props.getPort()).isEqualTo(GemFireVectorStore.DEFAULT_PORT);
		assertThat(props.isSslEnabled()).isEqualTo(false);
		assertThat(props.getConnectionTimeout()).isEqualTo(0);
		assertThat(props.getRequestTimeout()).isEqualTo(0);
		assertThat(props.getIndex()).isNull();
		assertThat(props.getTopK()).isEqualTo(GemFireVectorStore.DEFAULT_TOP_K);
		assertThat(props.getTopKPerBucket()).isEqualTo(GemFireVectorStore.DEFAULT_TOP_K_PER_BUCKET);
		assertThat(props.getDocumentField()).isEqualTo(GemFireVectorStore.DEFAULT_DOCUMENT_FIELD);
	}

	@Test
	void customValues() {
		var props = new GemFireVectorStoreProperties();
		props.setHost("127.0.0.1");
		props.setPort(9043);
		props.setSslEnabled(true);
		props.setConnectionTimeout(100);
		props.setRequestTimeout(200);
		props.setIndex("index");
		props.setTopK(10);
		props.setTopKPerBucket(20);
		props.setDocumentField("document");

		assertThat(props.getHost()).isEqualTo("127.0.0.1");
		assertThat(props.getPort()).isEqualTo(9043);
		assertThat(props.isSslEnabled()).isTrue();
		assertThat(props.getConnectionTimeout()).isEqualTo(100);
		assertThat(props.getRequestTimeout()).isEqualTo(200);
		assertThat(props.getIndex()).isEqualTo("index");
		assertThat(props.getTopK()).isEqualTo(10);
		assertThat(props.getTopKPerBucket()).isEqualTo(20);
		assertThat(props.getDocumentField()).isEqualTo("document");
	}

}
