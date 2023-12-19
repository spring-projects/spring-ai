package org.springframework.ai.bedrock.titan;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
class BedrockTitanEmbeddingClientIT {

	@Autowired
	private BedrockTitanEmbeddingClient embeddingClient;

	@Test
	void singleEmbedding() {
		assertThat(embeddingClient).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingClient.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getData()).hasSize(1);
		assertThat(embeddingResponse.getData().get(0).getEmbedding()).isNotEmpty();
		assertThat(embeddingClient.dimensions()).isEqualTo(1024);
	}

	@Test
	void batchEmbedding() {
		assertThatThrownBy(
				() -> embeddingClient.embedForResponse(List.of("Hello World", "World is big and salvation is near")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Titan Embedding does not support batch embedding!");
	}

	@Test
	void imageEmbedding() throws IOException {

		byte[] image = new DefaultResourceLoader().getResource("classpath:/spring_framework.png")
			.getContentAsByteArray();

		EmbeddingResponse embeddingResponse = embeddingClient
			.embedForResponse(List.of(Base64.getEncoder().encodeToString(image)));
		assertThat(embeddingResponse.getData()).hasSize(1);
		assertThat(embeddingResponse.getData().get(0).getEmbedding()).isNotEmpty();
		assertThat(embeddingClient.dimensions()).isEqualTo(1024);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public TitanEmbeddingBedrockApi titanEmbeddingApi() {
			return new TitanEmbeddingBedrockApi(TitanEmbeddingModel.TITAN_EMBED_IMAGE_V1.id(), Region.US_EAST_1.id());
		}

		@Bean
		public BedrockTitanEmbeddingClient titanEmbedding(TitanEmbeddingBedrockApi titanEmbeddingApi) {
			return new BedrockTitanEmbeddingClient(titanEmbeddingApi);
		}

	}

}
