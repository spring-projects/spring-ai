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

package org.springframework.ai.azure.openai.image;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

import org.springframework.ai.azure.openai.AzureOpenAiImageModel;
import org.springframework.ai.azure.openai.AzureOpenAiImageOptions;
import org.springframework.ai.azure.openai.metadata.AzureOpenAiImageGenerationMetadata;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOTE: use deployment ID dall-e-3
 */
@SpringBootTest
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_IMAGE_API_KEY", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_IMAGE_ENDPOINT", matches = ".+") })
public class AzureOpenAiImageModelIT {

	@Autowired
	protected ImageModel imageModel;

	@Test
	void imageAsUrlTest() {
		var options = ImageOptionsBuilder.builder().height(1024).width(1024).build();

		var instructions = """
				A light cream colored mini golden doodle with a sign that contains the message "I'm on my way to BARCADE!".""";

		ImagePrompt imagePrompt = new ImagePrompt(instructions, options);

		ImageResponse imageResponse = this.imageModel.call(imagePrompt);

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
			String apiKey = System.getenv("AZURE_OPENAI_IMAGE_API_KEY");
			String endpoint = System.getenv("AZURE_OPENAI_IMAGE_ENDPOINT");

			// System.out.println("API Key: " + apiKey);
			// System.out.println("Endpoint: " + endpoint);

			return new OpenAIClientBuilder().credential(new AzureKeyCredential(apiKey))
				.endpoint(endpoint)
				.buildClient();
		}

		@Bean
		public AzureOpenAiImageModel azureOpenAiImageModel(OpenAIClient openAIClient) {
			return new AzureOpenAiImageModel(openAIClient,
					AzureOpenAiImageOptions.builder().deploymentName("dall-e-3").build());

		}

	}

}
