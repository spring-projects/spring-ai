package org.springframework.ai.autoconfigure.vectorstore.typesense;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.TypesenseVectorStore;
import org.springframework.ai.vectorstore.TypesenseVectorStore.TypesenseVectorStoreConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Pablo Sanchidrian Herrera
 */
@AutoConfiguration
@ConditionalOnClass({ TypesenseVectorStore.class, EmbeddingModel.class })
@EnableConfigurationProperties({ TypesenseServiceClientProperties.class, TypesenseVectorStoreProperties.class })
public class TypesenseVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(TypesenseConnectionDetails.class)
	TypesenseVectorStoreAutoConfiguration.PropertiesTypesenseConnectionDetails typesenseServiceClientConnectionDetails(
			TypesenseServiceClientProperties properties) {
		return new TypesenseVectorStoreAutoConfiguration.PropertiesTypesenseConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public VectorStore vectorStore(Client typesenseClient, EmbeddingModel embeddingClient,
			TypesenseVectorStoreProperties properties) {

		TypesenseVectorStoreConfig config = TypesenseVectorStoreConfig.builder()
			.withCollectionName(properties.getCollectionName())
			.withEmbeddingDimension(properties.getEmbeddingDimension())
			.build();

		return new TypesenseVectorStore(typesenseClient, embeddingClient, config);
	}

	@Bean
	@ConditionalOnMissingBean
	public Client typesenseClient(TypesenseServiceClientProperties clientProperties,
			TypesenseConnectionDetails connectionDetails) {
		List<Node> nodes = new ArrayList<>();
		nodes.add(new Node(clientProperties.getProtocol(), clientProperties.getHost(), clientProperties.getPort()));

		Configuration configuration = new Configuration(nodes, Duration.ofSeconds(5), clientProperties.getApiKey());
		return new Client(configuration);
	}

	private static class PropertiesTypesenseConnectionDetails implements TypesenseConnectionDetails {

		private final TypesenseServiceClientProperties properties;

		PropertiesTypesenseConnectionDetails(TypesenseServiceClientProperties properties) {
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
