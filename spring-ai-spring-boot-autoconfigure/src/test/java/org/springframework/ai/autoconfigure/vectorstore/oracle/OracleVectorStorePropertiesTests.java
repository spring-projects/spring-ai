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

package org.springframework.ai.autoconfigure.vectorstore.oracle;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.oracle.OracleVectorStore;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore.OracleVectorStoreDistanceType;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore.OracleVectorStoreIndexType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class OracleVectorStorePropertiesTests {

	@Test
	public void defaultValues() {
		var props = new OracleVectorStoreProperties();
		assertThat(props.getDimensions()).isEqualTo(OracleVectorStore.DEFAULT_DIMENSIONS);
		assertThat(props.getDistanceType()).isEqualTo(OracleVectorStoreDistanceType.COSINE);
		assertThat(props.getIndexType()).isEqualTo(OracleVectorStoreIndexType.IVF);
		assertThat(props.isRemoveExistingVectorStoreTable()).isFalse();
	}

	@Test
	public void customValues() {
		var props = new OracleVectorStoreProperties();

		props.setDimensions(1536);
		props.setDistanceType(OracleVectorStoreDistanceType.EUCLIDEAN);
		props.setIndexType(OracleVectorStoreIndexType.IVF);
		props.setRemoveExistingVectorStoreTable(true);

		assertThat(props.getDimensions()).isEqualTo(1536);
		assertThat(props.getDistanceType()).isEqualTo(OracleVectorStoreDistanceType.EUCLIDEAN);
		assertThat(props.getIndexType()).isEqualTo(OracleVectorStoreIndexType.IVF);
		assertThat(props.isRemoveExistingVectorStoreTable()).isTrue();
	}

}
