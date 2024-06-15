package org.springframework.ai.azure.openai.image;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.azure.openai.AzureOpenAiImageModel;
import org.springframework.ai.azure.openai.AzureOpenAiImageOptions;
import org.springframework.ai.azure.openai.metadata.AzureOpenAiImageGenerationMetadata;
import org.springframework.ai.image.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AzureOpenAiImageModelIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
public class AzureOpenAiImageModelIT {

	@Autowired
	protected ImageModel imageModel;

	@Test
	void imageAsUrlTest() {
		var options = ImageOptionsBuilder.builder().withHeight(1024).withWidth(1024).build();

		var instructions = """
				A light cream colored mini golden doodle with a sign that contains the message "I'm on my way to BARCADE!".""";

		ImagePrompt imagePrompt = new ImagePrompt(instructions, options);

		ImageResponse imageResponse = imageModel.call(imagePrompt);

		assertThat(imageResponse.getResults()).hasSize(1);

		ImageResponseMetadata imageResponseMetadata = imageResponse.getMetadata();
		assertThat(imageResponseMetadata.getCreated()).isPositive();

		var generation = imageResponse.getResult();
		Image image = generation.getOutput();
		assertThat(image.getUrl()).isNotEmpty();
		// System.out.println(image.getUrl());
		assertThat(image.getB64Json()).isNull();

		var imageGenerationMetadata = generation.getMetadata();
		Assertions.assertThat(imageGenerationMetadata).isInstanceOf(AzureOpenAiImageGenerationMetadata.class);

		AzureOpenAiImageGenerationMetadata openAiImageGenerationMetadata = (AzureOpenAiImageGenerationMetadata) imageGenerationMetadata;

		assertThat(openAiImageGenerationMetadata).isNotNull();
		assertThat(openAiImageGenerationMetadata.getRevisedPrompt()).isNotBlank();
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OpenAIClient openAIClient() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
				.buildClient();
		}

		@Bean
		public AzureOpenAiImageModel azureOpenAiImageModel(OpenAIClient openAIClient) {
			return new AzureOpenAiImageModel(openAIClient,
					AzureOpenAiImageOptions.builder().withDeploymentName("Dalle3").build());

		}

	}

}
