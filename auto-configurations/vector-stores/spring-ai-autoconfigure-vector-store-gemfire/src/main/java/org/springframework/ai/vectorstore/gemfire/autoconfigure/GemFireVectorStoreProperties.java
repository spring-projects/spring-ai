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

package org.springframework.ai.vectorstore.gemfire.autoconfigure;

import org.springframework.ai.vectorstore.gemfire.GemFireVectorStore;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for GemFire Vector Store.
 *
 * @author Geet Rawat
 * @author Soby Chacko
 */
@ConfigurationProperties(GemFireVectorStoreProperties.CONFIG_PREFIX)
public class GemFireVectorStoreProperties extends CommonVectorStoreProperties {

	/**
	 * Configuration prefix for Spring AI VectorStore GemFire.
	 */
	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.gemfire";

	/**
	 * The host of the GemFire to connect to. To specify a custom host, use
	 * "spring.ai.vectorstore.gemfire.host";
	 *
	 */
	private String host = GemFireVectorStore.DEFAULT_HOST;

	/**
	 * The port of the GemFire to connect to. To specify a custom port, use
	 * "spring.ai.vectorstore.gemfire.port";
	 */
	private int port = GemFireVectorStore.DEFAULT_PORT;

	/**
	 * The name of the index in the GemFire. To specify a custom index, use
	 * "spring.ai.vectorstore.gemfire.index-name";
	 */
	private String indexName = GemFireVectorStore.DEFAULT_INDEX_NAME;

	/**
	 * The beam width for similarity queries. Default value is {@code 100}. To specify a
	 * custom beam width, use "spring.ai.vectorstore.gemfire.beam-width";
	 */
	private int beamWidth = GemFireVectorStore.DEFAULT_BEAM_WIDTH;

	/**
	 * The maximum number of connections allowed. Default value is {@code 16}. To specify
	 * custom number of connections, use "spring.ai.vectorstore.gemfire.max-connections";
	 */
	private int maxConnections = GemFireVectorStore.DEFAULT_MAX_CONNECTIONS;

	/**
	 * The similarity function to be used for vector comparisons. Default value is
	 * {@code "COSINE"}. To specify custom vectorSimilarityFunction, use
	 * "spring.ai.vectorstore.gemfire.vector-similarity-function";
	 *
	 */
	private String vectorSimilarityFunction = GemFireVectorStore.DEFAULT_SIMILARITY_FUNCTION;

	/**
	 * The fields to be used for queries. Default value is an array containing
	 * {@code "vector"}. To specify custom fields, use
	 * "spring.ai.vectorstore.gemfire.fields"
	 */
	private String[] fields = GemFireVectorStore.DEFAULT_FIELDS;

	/**
	 * The number of buckets to use for partitioning the data. Default value is {@code 0}.
	 *
	 * To specify custom buckets, use "spring.ai.vectorstore.gemfire.buckets";
	 *
	 */
	private int buckets = GemFireVectorStore.DEFAULT_BUCKETS;

	/**
	 * Set to true if GemFire cluster is ssl enabled
	 *
	 * To specify sslEnabled, use "spring.ai.vectorstore.gemfire.ssl-enabled";
	 */
	private boolean sslEnabled = GemFireVectorStore.DEFAULT_SSL_ENABLED;

	/**
	 * Configures the username for the GemFire VectorStore connection
	 *
	 * To specify username, use "spring.ai.vectorstore.gemfire.username";
	 */
	private String username;

	/**
	 * Configures the password for the GemFire VectorStore connection
	 *
	 * To specify password, use "spring.ai.vectorstore.gemfire.password";
	 */
	private String password;

	/**
	 * Configures the token for the GemFire VectorStore connection
	 *
	 * To specify token, use "spring.ai.vectorstore.gemfire.token";
	 */
	private String token;

	public int getBeamWidth() {
		return this.beamWidth;
	}

	public void setBeamWidth(int beamWidth) {
		this.beamWidth = beamWidth;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public int getMaxConnections() {
		return this.maxConnections;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public String getVectorSimilarityFunction() {
		return this.vectorSimilarityFunction;
	}

	public void setVectorSimilarityFunction(String vectorSimilarityFunction) {
		this.vectorSimilarityFunction = vectorSimilarityFunction;
	}

	public String[] getFields() {
		return this.fields;
	}

	public void setFields(String[] fields) {
		this.fields = fields;
	}

	public int getBuckets() {
		return this.buckets;
	}

	public void setBuckets(int buckets) {
		this.buckets = buckets;
	}

	public boolean isSslEnabled() {
		return this.sslEnabled;
	}

	public void setSslEnabled(boolean sslEnabled) {
		this.sslEnabled = sslEnabled;
	}

	public String getToken() {
		return this.token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

}
