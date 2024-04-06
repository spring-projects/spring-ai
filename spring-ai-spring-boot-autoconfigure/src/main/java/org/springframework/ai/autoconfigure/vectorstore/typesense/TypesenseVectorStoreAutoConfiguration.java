package org.springframework.ai.autoconfigure.vectorstore.typesense;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({ EmbeddingClient.class })
@EnableConfigurationProperties({ TypesenseVectorStoreProperties.class })
public class TypesenseVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(TypesenseConnectionDetails.class)
	public PropertiesTypesenseConnectionDetails typesenseConnectionDetails(TypesenseVectorStoreProperties properties) {
		return new PropertiesTypesenseConnectionDetails(properties);
	}

	private static class PropertiesTypesenseConnectionDetails implements TypesenseConnectionDetails {

		private final TypesenseVectorStoreProperties properties;

		PropertiesTypesenseConnectionDetails(TypesenseVectorStoreProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getProtocol() {
			return this.properties.getProtocol();
		}

		@Override
		public String getHost() {
			return this.properties.getHost();
		}

		@Override
		public String getPort() {
			return this.properties.getPort();
		}

	}

}
