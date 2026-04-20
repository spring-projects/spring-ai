/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.arcadedb.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the ArcadeDB VectorStore.
 *
 * @author Luca Garulli
 * @since 2.0.0
 */
@ConfigurationProperties(ArcadeDBVectorStoreProperties.CONFIG_PREFIX)
public class ArcadeDBVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.arcadedb";

	private String databasePath;

	private String typeName = "Document";

	private int embeddingDimension = 1536;

	private String distanceType = "COSINE";

	private boolean initializeSchema = true;

	private int m = 16;

	private int ef = 10;

	private int efConstruction = 200;

	private String metadataPrefix = "meta_";

	public String getDatabasePath() {
		return databasePath;
	}

	public void setDatabasePath(String databasePath) {
		this.databasePath = databasePath;
	}

	public String getTypeName() {
		return typeName;
	}

	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}

	public int getEmbeddingDimension() {
		return embeddingDimension;
	}

	public void setEmbeddingDimension(int embeddingDimension) {
		this.embeddingDimension = embeddingDimension;
	}

	public String getDistanceType() {
		return distanceType;
	}

	public void setDistanceType(String distanceType) {
		this.distanceType = distanceType;
	}

	public boolean isInitializeSchema() {
		return initializeSchema;
	}

	public void setInitializeSchema(boolean initializeSchema) {
		this.initializeSchema = initializeSchema;
	}

	public int getM() {
		return m;
	}

	public void setM(int m) {
		this.m = m;
	}

	public int getEf() {
		return ef;
	}

	public void setEf(int ef) {
		this.ef = ef;
	}

	public int getEfConstruction() {
		return efConstruction;
	}

	public void setEfConstruction(int efConstruction) {
		this.efConstruction = efConstruction;
	}

	public String getMetadataPrefix() {
		return metadataPrefix;
	}

	public void setMetadataPrefix(String metadataPrefix) {
		this.metadataPrefix = metadataPrefix;
	}

}
