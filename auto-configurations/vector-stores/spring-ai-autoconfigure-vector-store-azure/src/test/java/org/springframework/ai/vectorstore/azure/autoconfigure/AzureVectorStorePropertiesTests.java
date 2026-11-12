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

package org.springframework.ai.vectorstore.azure.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.azure.AzureVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Karan Bhardwaj
 */
class AzureVectorStorePropertiesTests {

	@Test
	void defaultMetadataFieldsIsEmpty() {
		var props = new AzureVectorStoreProperties();
		assertThat(props.getMetadataFields()).isEmpty();
	}

	@Test
	void metadataFieldsCanBeSet() {
		var props = new AzureVectorStoreProperties();

		var entry1 = new AzureVectorStoreProperties.MetadataFieldEntry();
		entry1.setName("department");
		entry1.setFieldType("string");

		var entry2 = new AzureVectorStoreProperties.MetadataFieldEntry();
		entry2.setName("year");
		entry2.setFieldType("int64");

		props.setMetadataFields(List.of(entry1, entry2));

		assertThat(props.getMetadataFields()).hasSize(2);
		assertThat(props.getMetadataFields().get(0).getName()).isEqualTo("department");
		assertThat(props.getMetadataFields().get(0).getFieldType()).isEqualTo("string");
		assertThat(props.getMetadataFields().get(1).getName()).isEqualTo("year");
		assertThat(props.getMetadataFields().get(1).getFieldType()).isEqualTo("int64");
	}

	@Test
	void metadataFieldsDefaultValues() {
		var props = new AzureVectorStoreProperties();
		assertThat(props.getIndexName()).isEqualTo(AzureVectorStore.DEFAULT_INDEX_NAME);
	}

	@Test
	void metadataFiledsTypoAliasWorks() {
		var props = new AzureVectorStoreProperties();

		var entry = new AzureVectorStoreProperties.MetadataFieldEntry();
		entry.setName("filterField");
		entry.setFieldType("string");

		props.setMetadataFileds(List.of(entry));

		assertThat(props.getMetadataFields()).hasSize(1);
		assertThat(props.getMetadataFields().get(0).getName()).isEqualTo("filterField");
		assertThat(props.getMetadataFields().get(0).getFieldType()).isEqualTo("string");
	}

}
