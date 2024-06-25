package org.springframework.ai.vectorstore;

import org.springframework.util.Assert;

public final class GemFireVectorStoreConfig {

	// Create Index DEFAULT Values
	public static final String DEFAULT_HOST = "localhost";

	public static final int DEFAULT_PORT = 8080;

	public static final String DEFAULT_INDEX_NAME = "spring-ai-gemfire-index";

	public static final int UPPER_BOUND_BEAM_WIDTH = 3200;

	public static final int DEFAULT_BEAM_WIDTH = 100;

	private static final int UPPER_BOUND_MAX_CONNECTIONS = 512;

	public static final int DEFAULT_MAX_CONNECTIONS = 16;

	public static final String DEFAULT_SIMILARITY_FUNCTION = "COSINE";

	public static final String[] DEFAULT_FIELDS = new String[] {};

	public static final int DEFAULT_BUCKETS = 0;

	public static final boolean DEFAULT_SSL_ENABLED = false;

	String host = GemFireVectorStoreConfig.DEFAULT_HOST;

	int port = DEFAULT_PORT;

	String indexName = DEFAULT_INDEX_NAME;

	int beamWidth = DEFAULT_BEAM_WIDTH;

	int maxConnections = DEFAULT_MAX_CONNECTIONS;

	String vectorSimilarityFunction = DEFAULT_SIMILARITY_FUNCTION;

	String[] fields = DEFAULT_FIELDS;

	int buckets = DEFAULT_BUCKETS;

	boolean sslEnabled = DEFAULT_SSL_ENABLED;

	public GemFireVectorStoreConfig() {
	}

	public GemFireVectorStoreConfig setHost(String host) {
		Assert.hasText(host, "host must have a value");
		this.host = host;
		return this;
	}

	public GemFireVectorStoreConfig setPort(int port) {
		Assert.isTrue(port > 0, "port must be positive");
		this.port = port;
		return this;
	}

	public GemFireVectorStoreConfig setSslEnabled(boolean sslEnabled) {
		this.sslEnabled = sslEnabled;
		return this;
	}

	public GemFireVectorStoreConfig setIndexName(String indexName) {
		Assert.hasText(indexName, "indexName must have a value");
		this.indexName = indexName;
		return this;
	}

	public GemFireVectorStoreConfig setBeamWidth(int beamWidth) {
		Assert.isTrue(beamWidth > 0, "beamWidth must be positive");
		Assert.isTrue(beamWidth <= GemFireVectorStoreConfig.UPPER_BOUND_BEAM_WIDTH,
				"beamWidth must be less than or equal to " + GemFireVectorStoreConfig.UPPER_BOUND_BEAM_WIDTH);
		this.beamWidth = beamWidth;
		return this;
	}

	public GemFireVectorStoreConfig setMaxConnections(int maxConnections) {
		Assert.isTrue(maxConnections > 0, "maxConnections must be positive");
		Assert.isTrue(maxConnections <= GemFireVectorStoreConfig.UPPER_BOUND_MAX_CONNECTIONS,
				"maxConnections must be less than or equal to " + GemFireVectorStoreConfig.UPPER_BOUND_MAX_CONNECTIONS);
		this.maxConnections = maxConnections;
		return this;
	}

	public GemFireVectorStoreConfig setBuckets(int buckets) {
		Assert.isTrue(buckets >= 0, "bucket must be 1 or more");
		this.buckets = buckets;
		return this;
	}

	public GemFireVectorStoreConfig setVectorSimilarityFunction(String vectorSimilarityFunction) {
		Assert.hasText(vectorSimilarityFunction, "vectorSimilarityFunction must have a value");
		this.vectorSimilarityFunction = vectorSimilarityFunction;
		return this;
	}

	public GemFireVectorStoreConfig setFields(String[] fields) {
		this.fields = fields;
		return this;
	}

}
