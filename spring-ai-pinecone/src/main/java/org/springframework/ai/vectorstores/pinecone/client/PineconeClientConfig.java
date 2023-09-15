package org.springframework.ai.vectorstores.pinecone.client;

import io.pinecone.PineconeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PineconeClientConfig {

	@Value("${pinecone.api-key}")
	private String apiKey;

	@Value("${pinecone.environment}")
	private String environment;

	@Value("${pinecone.project-name}")
	private String projectName;

	@Bean
	public PineconeClient pineconeClient() {
		io.pinecone.PineconeClientConfig configuration = new io.pinecone.PineconeClientConfig().withApiKey(apiKey)
			.withEnvironment(environment)
			.withProjectName(projectName)
			.withServerSideTimeoutSec(10);
		PineconeClient pineconeClient = new PineconeClient(configuration);
		return new PineconeClient(configuration);
	}

}