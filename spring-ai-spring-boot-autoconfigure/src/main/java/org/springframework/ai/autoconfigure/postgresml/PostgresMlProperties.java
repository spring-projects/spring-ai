/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.ai.autoconfigure.postgresml;

import java.util.Collections;
import java.util.Map;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.postgresml.PostgresMlEmbeddingClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Configuration properties for Postgres ML.
 */
@ConfigurationProperties(PostgresMlProperties.CONFIG_PREFIX)
public class PostgresMlProperties {

	public static final String CONFIG_PREFIX = "spring.ai.postgresml";

	private final PostgresMlProperties.Embedding embedding = new PostgresMlProperties.Embedding(this);

	private String transformer = "distilbert-base-uncased";

	private PostgresMlEmbeddingClient.VectorType vectorType = PostgresMlEmbeddingClient.VectorType.PG_ARRAY;

	private Map<String, Object> kwargs = Collections.emptyMap();

	private MetadataMode metadataMode = MetadataMode.EMBED;

	public PostgresMlProperties.Embedding getEmbedding() {
		return this.embedding;
	}

	public String getTransformer() {
		return transformer;
	}

	public void setTransformer(String transformer) {
		this.transformer = transformer;
	}

	public PostgresMlEmbeddingClient.VectorType getVectorType() {
		return vectorType;
	}

	public void setVectorType(PostgresMlEmbeddingClient.VectorType vectorType) {
		this.vectorType = vectorType;
	}

	public Map<String, Object> getKwargs() {
		return kwargs;
	}

	public void setKwargs(Map<String, Object> kwargs) {
		this.kwargs = kwargs;
	}

	public MetadataMode getMetadataMode() {
		return metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	public static class Embedding {

		private PostgresMlProperties postgresMlProperties;

		private String transformer;

		private PostgresMlEmbeddingClient.VectorType vectorType;

		private Map<String, Object> kwargs;

		private MetadataMode metadataMode;

		protected Embedding(PostgresMlProperties postgresMlProperties) {
			Assert.notNull(postgresMlProperties, "PostgresMlProperties must not be null");
			this.postgresMlProperties = postgresMlProperties;
		}

		public PostgresMlProperties getPostgresMlProperties() {
			return postgresMlProperties;
		}

		public String getTransformer() {
			return StringUtils.hasText(this.transformer) ? this.transformer
					: getPostgresMlProperties().getTransformer();
		}

		public void setTransformer(String transformer) {
			this.transformer = transformer;
		}

		public PostgresMlEmbeddingClient.VectorType getVectorType() {
			return this.vectorType != null ? this.vectorType : getPostgresMlProperties().getVectorType();
		}

		public void setVectorType(PostgresMlEmbeddingClient.VectorType vectorType) {
			this.vectorType = vectorType;
		}

		public Map<String, Object> getKwargs() {
			return this.kwargs != null ? this.kwargs : getPostgresMlProperties().getKwargs();
		}

		public void setKwargs(Map<String, Object> kwargs) {
			this.kwargs = kwargs;
		}

		public MetadataMode getMetadataMode() {
			return this.metadataMode != null ? this.metadataMode : getPostgresMlProperties().getMetadataMode();
		}

		public void setMetadataMode(MetadataMode metadataMode) {
			this.metadataMode = metadataMode;
		}

	}

	public static class Metadata {

		private Boolean rateLimitMetricsEnabled;

		public boolean isRateLimitMetricsEnabled() {
			return Boolean.TRUE.equals(getRateLimitMetricsEnabled());
		}

		public Boolean getRateLimitMetricsEnabled() {
			return this.rateLimitMetricsEnabled;
		}

		public void setRateLimitMetricsEnabled(Boolean rateLimitMetricsEnabled) {
			this.rateLimitMetricsEnabled = rateLimitMetricsEnabled;
		}

	}

}
