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

package org.springframework.ai.autoconfigure.vectorstore.gemfire;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Geet Rawat
 */
@ConfigurationProperties(GemFireVectorStoreProperties.CONFIG_PREFIX)
public class GemFireVectorStoreProperties {

	/**
	 * Configuration prefix for Spring AI VectorStore GemFire.
	 */
	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.gemfire";

	/**
	 * The host of the GemFire to connect to.
	 */
	private String host;

	/**
	 * The port of the GemFire to connect to.
	 */
	private int port;

	/**
	 * The name of the index in the GemFire.
	 */
	private String indexName;

	/**
	 * The beam width for similarity queries. Default value is {@code 100}.
	 */
	private int beamWidth = 100;

	/**
	 * The maximum number of connections allowed. Default value is {@code 16}.
	 */
	private int maxConnections = 16;

	/**
	 * The similarity function to be used for vector comparisons. Default value is
	 * {@code "COSINE"}.
	 */
	private String vectorSimilarityFunction = "COSINE";

	/**
	 * The fields to be used for queries. Default value is an array containing
	 * {@code "vector"}.
	 */
	private String[] fields = new String[] {};

	/**
	 * The number of buckets to use for partitioning the data. Default value is {@code 0}.
	 */
	private int buckets = 0;

	public int getBeamWidth() {
		return beamWidth;
	}

	public void setBeamWidth(int beamWidth) {
		this.beamWidth = beamWidth;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public int getMaxConnections() {
		return maxConnections;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public String getVectorSimilarityFunction() {
		return vectorSimilarityFunction;
	}

	public void setVectorSimilarityFunction(String vectorSimilarityFunction) {
		this.vectorSimilarityFunction = vectorSimilarityFunction;
	}

	public String[] getFields() {
		return fields;
	}

	public void setFields(String[] fields) {
		this.fields = fields;
	}

	public int getBuckets() {
		return buckets;
	}

	public void setBuckets(int buckets) {
		this.buckets = buckets;
	}

}
