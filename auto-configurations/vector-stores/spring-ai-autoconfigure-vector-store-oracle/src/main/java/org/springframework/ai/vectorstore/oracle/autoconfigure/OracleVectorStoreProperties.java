/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.vectorstore.oracle.autoconfigure;

import org.springframework.ai.vectorstore.oracle.OracleVectorStore;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Oracle Vector Store.
 *
 * @author Loïc Lefèvre
 * @author Anders Swanson
 */
@ConfigurationProperties(OracleVectorStoreProperties.CONFIG_PREFIX)
public class OracleVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.oracle";

	private String tableName = OracleVectorStore.DEFAULT_TABLE_NAME;

	private OracleVectorStore.OracleVectorStoreIndexType indexType = OracleVectorStore.DEFAULT_INDEX_TYPE;

	private OracleVectorStore.OracleVectorStoreDistanceType distanceType = OracleVectorStore.DEFAULT_DISTANCE_TYPE;

	private int dimensions = OracleVectorStore.DEFAULT_DIMENSIONS;

	private boolean removeExistingVectorStoreTable;

	private boolean forcedNormalization;

	private int searchAccuracy = OracleVectorStore.DEFAULT_SEARCH_ACCURACY;

	private int hnswNeighbors = OracleVectorStore.DEFAULT_HNSW_NEIGHBORS;

	private int hnswEfConstruction = OracleVectorStore.DEFAULT_HNSW_EF_CONSTRUCTION;

	private int ivfNeighborPartitions = OracleVectorStore.DEFAULT_IVF_NEIGHBOR_PARTITIONS;

	private int ivfSamplePerPartition = OracleVectorStore.DEFAULT_IVF_SAMPLE_PER_PARTITION;

	private int ivfMinVectorsPerPartition = OracleVectorStore.DEFAULT_IVF_MIN_VECTORS_PER_PARTITION;

	public String getTableName() {
		return this.tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public OracleVectorStore.OracleVectorStoreIndexType getIndexType() {
		return this.indexType;
	}

	public void setIndexType(OracleVectorStore.OracleVectorStoreIndexType indexType) {
		this.indexType = indexType;
	}

	public OracleVectorStore.OracleVectorStoreDistanceType getDistanceType() {
		return this.distanceType;
	}

	public void setDistanceType(OracleVectorStore.OracleVectorStoreDistanceType distanceType) {
		this.distanceType = distanceType;
	}

	public int getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(int dimensions) {
		this.dimensions = dimensions;
	}

	public boolean isRemoveExistingVectorStoreTable() {
		return this.removeExistingVectorStoreTable;
	}

	public void setRemoveExistingVectorStoreTable(boolean removeExistingVectorStoreTable) {
		this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
	}

	public boolean isForcedNormalization() {
		return this.forcedNormalization;
	}

	public void setForcedNormalization(boolean forcedNormalization) {
		this.forcedNormalization = forcedNormalization;
	}

	public int getSearchAccuracy() {
		return this.searchAccuracy;
	}

	public void setSearchAccuracy(int searchAccuracy) {
		this.searchAccuracy = searchAccuracy;
	}

	/**
	 * Returns the configured HNSW neighbors value.
	 * @return the configured HNSW neighbors value
	 * @since 2.0.0
	 */
	public int getHnswNeighbors() {
		return this.hnswNeighbors;
	}

	/**
	 * Sets the HNSW neighbors value.
	 * @param hnswNeighbors the HNSW neighbors value
	 * @since 2.0.0
	 */
	public void setHnswNeighbors(int hnswNeighbors) {
		this.hnswNeighbors = hnswNeighbors;
	}

	/**
	 * Returns the configured HNSW efConstruction value.
	 * @return the configured HNSW efConstruction value
	 * @since 2.0.0
	 */
	public int getHnswEfConstruction() {
		return this.hnswEfConstruction;
	}

	/**
	 * Sets the HNSW efConstruction value.
	 * @param hnswEfConstruction the HNSW efConstruction value
	 * @since 2.0.0
	 */
	public void setHnswEfConstruction(int hnswEfConstruction) {
		this.hnswEfConstruction = hnswEfConstruction;
	}

	/**
	 * Returns the configured IVF neighbor partitions value.
	 * @return the configured IVF neighbor partitions value
	 * @since 2.0.0
	 */
	public int getIvfNeighborPartitions() {
		return this.ivfNeighborPartitions;
	}

	/**
	 * Sets the IVF neighbor partitions value.
	 * @param ivfNeighborPartitions the IVF neighbor partitions value
	 * @since 2.0.0
	 */
	public void setIvfNeighborPartitions(int ivfNeighborPartitions) {
		this.ivfNeighborPartitions = ivfNeighborPartitions;
	}

	/**
	 * Returns the configured IVF sample per partition value.
	 * @return the configured IVF sample per partition value
	 * @since 2.0.0
	 */
	public int getIvfSamplePerPartition() {
		return this.ivfSamplePerPartition;
	}

	/**
	 * Sets the IVF sample per partition value.
	 * @param ivfSamplePerPartition the IVF sample per partition value
	 * @since 2.0.0
	 */
	public void setIvfSamplePerPartition(int ivfSamplePerPartition) {
		this.ivfSamplePerPartition = ivfSamplePerPartition;
	}

	/**
	 * Returns the configured IVF minimum vectors per partition value.
	 * @return the configured IVF minimum vectors per partition value
	 * @since 2.0.0
	 */
	public int getIvfMinVectorsPerPartition() {
		return this.ivfMinVectorsPerPartition;
	}

	/**
	 * Sets the IVF minimum vectors per partition value.
	 * @param ivfMinVectorsPerPartition the IVF minimum vectors per partition value
	 * @since 2.0.0
	 */
	public void setIvfMinVectorsPerPartition(int ivfMinVectorsPerPartition) {
		this.ivfMinVectorsPerPartition = ivfMinVectorsPerPartition;
	}

}
