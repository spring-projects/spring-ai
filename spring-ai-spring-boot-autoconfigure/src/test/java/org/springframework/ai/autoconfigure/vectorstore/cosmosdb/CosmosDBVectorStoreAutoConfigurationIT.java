package org.springframework.ai.autoconfigure.vectorstore.cosmosdb;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.CosmosDBEmulatorContainer;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

//@Testcontainers
@EnableAutoConfiguration
public class CosmosDBVectorStoreAutoConfigurationIT {

	// static final DockerImageName DEFAULT_IMAGE_NAME =
	// DockerImageName.parse("mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:latest");

	// @Container
	// static CosmosDBEmulatorContainer cosmosDBEmulatorContainer = new
	// CosmosDBEmulatorContainer(DEFAULT_IMAGE_NAME);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(CosmosDBVectorStoreAutoConfiguration.class))
		.withPropertyValues("spring.ai.vectorstore.cosmosdb.databaseName=test-database")
		.withPropertyValues("spring.ai.vectorstore.cosmosdb.containerName=test-container")
		.withPropertyValues("spring.ai.vectorstore.cosmosdb.partitionKeyPath=/id")
		.withPropertyValues("spring.ai.vectorstore.cosmosdb.metadataFields=country,year,city")
		.withPropertyValues("spring.ai.vectorstore.cosmosdb.vectorStoreThoughput=1000")
		.withPropertyValues("spring.ai.vectorstore.cosmosdb.endpoint=" + System.getenv("COSMOSDB_AI_ENDPOINT"))
		.withPropertyValues("spring.ai.vectorstore.cosmosdb.key=" + System.getenv("COSMOSDB_AI_KEY"))
		.withUserConfiguration(Config.class);

	private VectorStore vectorStore;

	@BeforeEach
	public void setup() {
		// emulatorSetup(cosmosDBEmulatorContainer);
		contextRunner.run(context -> {
			vectorStore = context.getBean(VectorStore.class);
		});
	}

	private void emulatorSetup(CosmosDBEmulatorContainer cosmosDBEmulatorContainer) {
		cosmosDBEmulatorContainer.start();
		while (!cosmosDBEmulatorContainer.isRunning()) {
			try {
				System.out.println("Waiting for CosmosDB Emulator to start...");
				Thread.sleep(1000);
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		Files tempFolder = null;
		Path keyStoreFile = Files.newFile("v1-azure-cosmos-emulator.keystore").toPath();

		// Retry loop for buildNewKeyStore()
		KeyStore keyStore = null;
		while (true) {
			try {
				System.out.println("Tyring to get keystore from emulator...");
				keyStore = cosmosDBEmulatorContainer.buildNewKeyStore();
				System.out.println("Got keystore from emulator...");
				break; // If no exception is thrown, break the loop
			}
			catch (IllegalStateException e) {
				System.out.println("Failed getting keystore from emulator...");
				try {
					Thread.sleep(10000); // Sleep for 1 second before retrying
				}
				catch (InterruptedException ie) {
					throw new RuntimeException(ie);
				}
			}

		}

		try {
			keyStore.store(new FileOutputStream(keyStoreFile.toFile()),
					cosmosDBEmulatorContainer.getEmulatorKey().toCharArray());
		}
		catch (KeyStoreException e) {
			throw new RuntimeException(e);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		catch (CertificateException e) {
			throw new RuntimeException(e);
		}

		System.setProperty("javax.net.ssl.trustStore", keyStoreFile.toString());
		System.setProperty("javax.net.ssl.trustStorePassword", cosmosDBEmulatorContainer.getEmulatorKey());
		System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
		while (true) {
			try {
				System.out.println("Tyring to ping data explorer endpoint: "
						+ cosmosDBEmulatorContainer.getEmulatorEndpoint() + "/_explorer/index.html");
				// Specify the endpoint URL
				String endpointUrl = cosmosDBEmulatorContainer.getEmulatorEndpoint() + "/_explorer/index.html";

				// Create a URL object with the specified endpoint
				URL url = new URL(endpointUrl);

				// Open a connection to the URL
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();

				// Set the request method to GET
				connection.setRequestMethod("GET");

				// Set headers if needed
				connection.setRequestProperty("User-Agent", "Mozilla/5.0");
				connection.setRequestProperty("Accept", "application/json");

				// Get the response code
				int responseCode = connection.getResponseCode();
				System.out.println("Response Code: " + responseCode);

				// If the response code is 200 (HTTP_OK), read the response
				if (responseCode == HttpURLConnection.HTTP_OK) {
					BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
					String inputLine;
					StringBuffer response = new StringBuffer();

					while ((inputLine = in.readLine()) != null) {
						response.append(inputLine);
					}
					in.close();

					// Print the response
					System.out.println("Response: " + response);
				}
				else {
					System.out.println("GET request failed.");
				}
				break; // If no exception is thrown, break the loop
			}
			catch (Exception e) {
				System.out.println("Failed creating client from emulator endpoint...");
				try {
					Thread.sleep(10000); // Sleep for 1 second before retrying
				}
				catch (InterruptedException ie) {
					throw new RuntimeException(ie);
				}
			}

		}

		Files.delete(keyStoreFile.toFile());
	}

	@Test
	public void testAddSearchAndDeleteDocuments() {

		// Create a sample document
		Document document1 = new Document(UUID.randomUUID().toString(), "Sample content1", Map.of("key1", "value1"));
		Document document2 = new Document(UUID.randomUUID().toString(), "Sample content2", Map.of("key2", "value2"));

		// Add the document to the vector store
		vectorStore.add(List.of(document1, document2));

		// Perform a similarity search
		List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Sample content").withTopK(1));

		// Verify the search results
		assertThat(results).isNotEmpty();
		assertThat(results.get(0).getId()).isEqualTo(document1.getId());

		// Remove the documents from the vector store
		vectorStore.delete(List.of(document1.getId(), document2.getId()));

		// Perform a similarity search again
		List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("Sample content").withTopK(1));

		// Verify the search results
		assertThat(results2).isEmpty();
	}

	@Test
	void testSimilaritySearchWithFilter() {

		// Insert documents using vectorStore.add
		Map<String, Object> metadata1;
		metadata1 = new HashMap<>();
		metadata1.put("country", "UK");
		metadata1.put("year", 2021);
		metadata1.put("city", "London");

		Map<String, Object> metadata2;
		metadata2 = new HashMap<>();
		metadata2.put("country", "NL");
		metadata2.put("year", 2022);
		metadata2.put("city", "Amsterdam");

		Map<String, Object> metadata3;
		metadata3 = new HashMap<>();
		metadata3.put("country", "US");
		metadata3.put("year", 2019);
		metadata3.put("city", "Sofia");

		Map<String, Object> metadata4;
		metadata4 = new HashMap<>();
		metadata4.put("country", "US");
		metadata4.put("year", 2020);
		metadata4.put("city", "Sofia");

		Document document1 = new Document("1", "A document about the UK", metadata1);
		Document document2 = new Document("2", "A document about the Netherlands", metadata2);
		Document document3 = new Document("3", "A document about the US", metadata3);
		Document document4 = new Document("4", "A document about the US", metadata4);

		vectorStore.add(List.of(document1, document2, document3, document4));
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Document> results = vectorStore.similaritySearch(SearchRequest.query("The World")
			.withTopK(10)
			.withFilterExpression((b.in("country", "UK", "NL")).build()));

		assertThat(results).hasSize(2);
		assertThat(results).extracting(Document::getId).containsExactlyInAnyOrder("1", "2");

		List<Document> results2 = vectorStore.similaritySearch(SearchRequest.query("The World")
			.withTopK(10)
			.withFilterExpression(
					b.and(b.or(b.gte("year", 2021), b.eq("country", "NL")), b.ne("city", "Amsterdam")).build()));

		assertThat(results2).hasSize(1);
		assertThat(results2).extracting(Document::getId).containsExactlyInAnyOrder("1");

		vectorStore.delete(List.of(document1.getId(), document2.getId(), document3.getId(), document4.getId()));
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

	}

}
