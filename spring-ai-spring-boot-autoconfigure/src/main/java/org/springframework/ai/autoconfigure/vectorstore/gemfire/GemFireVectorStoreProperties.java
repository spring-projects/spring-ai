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

import org.springframework.ai.vectorstore.GemFireVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Philipp Kessler
 */
@ConfigurationProperties(GemFireVectorStoreProperties.CONFIG_PREFIX)
public class GemFireVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.gemfire";

	private String host;

	private int port = GemFireVectorStore.DEFAULT_PORT;

	private boolean sslEnabled;

	private long connectionTimeout;

	private long requestTimeout;

	private String index;

	private int topK = GemFireVectorStore.DEFAULT_TOP_K;

	private int topKPerBucket = GemFireVectorStore.DEFAULT_TOP_K_PER_BUCKET;

	private String documentField = GemFireVectorStore.DEFAULT_DOCUMENT_FIELD;

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isSslEnabled() {
		return sslEnabled;
	}

	public void setSslEnabled(boolean sslEnabled) {
		this.sslEnabled = sslEnabled;
	}

	public long getConnectionTimeout() {
		return connectionTimeout;
	}

	public void setConnectionTimeout(long connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public long getRequestTimeout() {
		return requestTimeout;
	}

	public void setRequestTimeout(long requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public String getIndex() {
		return index;
	}

	public void setIndex(String index) {
		this.index = index;
	}

	public int getTopKPerBucket() {
		return topKPerBucket;
	}

	public void setTopKPerBucket(int topKPerBucket) {
		this.topKPerBucket = topKPerBucket;
	}

	public int getTopK() {
		return topK;
	}

	public void setTopK(int topK) {
		this.topK = topK;
	}

	public String getDocumentField() {
		return documentField;
	}

	public void setDocumentField(String documentField) {
		this.documentField = documentField;
	}

}
