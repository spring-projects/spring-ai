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

package org.springframework.ai.autoconfigure.vectorstore.pinecone;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.PineconeVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class PineconeVectorStorePropertiesTests {

	@Test
	public void defaultValues() {
		var props = new PineconeVectorStoreProperties();
		assertThat(props.getEnvironment()).isEqualTo("gcp-starter");
		assertThat(props.getNamespace()).isEqualTo("");
		assertThat(props.getApiKey()).isNull();
		assertThat(props.getProjectId()).isNull();
		assertThat(props.getIndexName()).isNull();
		assertThat(props.getServerSideTimeout()).isEqualTo(Duration.ofSeconds(20));
		assertThat(props.getContentFieldName()).isEqualTo(PineconeVectorStore.CONTENT_FIELD_NAME);
		assertThat(props.getDistanceMetadataFieldName()).isEqualTo(PineconeVectorStore.DISTANCE_METADATA_FIELD_NAME);
	}

	@Test
	public void customValues() {
		var props = new PineconeVectorStoreProperties();
		props.setApiKey("key");
		props.setEnvironment("env");
		props.setIndexName("index");
		props.setNamespace("namespace");
		props.setProjectId("project");
		props.setServerSideTimeout(Duration.ofSeconds(60));
		props.setContentFieldName("article");
		props.setDistanceMetadataFieldName("distance2");

		assertThat(props.getEnvironment()).isEqualTo("env");
		assertThat(props.getNamespace()).isEqualTo("namespace");
		assertThat(props.getApiKey()).isEqualTo("key");
		assertThat(props.getProjectId()).isEqualTo("project");
		assertThat(props.getIndexName()).isEqualTo("index");
		assertThat(props.getServerSideTimeout()).isEqualTo(Duration.ofSeconds(60));
		assertThat(props.getContentFieldName()).isEqualTo("article");
		assertThat(props.getDistanceMetadataFieldName()).isEqualTo("distance2");
	}

}
