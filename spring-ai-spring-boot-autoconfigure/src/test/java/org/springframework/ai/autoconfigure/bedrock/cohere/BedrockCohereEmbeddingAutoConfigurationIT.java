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

package org.springframework.ai.autoconfigure.bedrock.cohere;

import java.util.List;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.autoconfigure.bedrock.BedrockTestUtils;
import org.springframework.ai.autoconfigure.bedrock.RequiresAwsCredentials;
import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Mark Pollack
 * @since 1.0.0
 */
@RequiresAwsCredentials
public class BedrockCohereEmbeddingAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = BedrockTestUtils.getContextRunner()
		.withPropertyValues("spring.ai.bedrock.cohere.embedding.enabled=true",
				"spring.ai.bedrock.cohere.embedding.model=" + CohereEmbeddingModel.COHERE_EMBED_MULTILINGUAL_V1.id(),
				"spring.ai.bedrock.cohere.embedding.options.inputType=SEARCH_DOCUMENT",
				"spring.ai.bedrock.cohere.embedding.options.truncate=NONE")
		.withConfiguration(AutoConfigurations.of(BedrockCohereEmbeddingAutoConfiguration.class));

	@Test
	public void singleEmbedding() {
		this.contextRunner.run(context -> {
			BedrockCohereEmbeddingModel embeddingModel = context.getBean(BedrockCohereEmbeddingModel.class);
			assertThat(embeddingModel).isNotNull();
			EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of("Hello World"));
			assertThat(embeddingResponse.getResults()).hasSize(1);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingModel.dimensions()).isEqualTo(1024);
		});
	}

	@Test
	public void batchEmbedding() {
		this.contextRunner.run(context -> {

			BedrockCohereEmbeddingModel embeddingModel = context.getBean(BedrockCohereEmbeddingModel.class);

			assertThat(embeddingModel).isNotNull();
			EmbeddingResponse embeddingResponse = embeddingModel
				.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

			assertThat(embeddingModel.dimensions()).isEqualTo(1024);

		});
	}

	@Test
	public void propertiesTest() {

		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.cohere.embedding.enabled=true",
					"spring.ai.bedrock.aws.access-key=ACCESS_KEY", "spring.ai.bedrock.aws.secret-key=SECRET_KEY",
					"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
					"spring.ai.bedrock.cohere.embedding.model=MODEL_XYZ",
					"spring.ai.bedrock.cohere.embedding.options.inputType=CLASSIFICATION",
					"spring.ai.bedrock.cohere.embedding.options.truncate=START")
			.withConfiguration(AutoConfigurations.of(BedrockCohereEmbeddingAutoConfiguration.class))
			.run(context -> {
				var properties = context.getBean(BedrockCohereEmbeddingProperties.class);
				var awsProperties = context.getBean(BedrockAwsConnectionProperties.class);

				assertThat(properties.isEnabled()).isTrue();
				assertThat(awsProperties.getRegion()).isEqualTo(Region.US_EAST_1.id());
				assertThat(properties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(properties.getOptions().getInputType()).isEqualTo(InputType.CLASSIFICATION);
				assertThat(properties.getOptions().getTruncate()).isEqualTo(CohereEmbeddingRequest.Truncate.START);

				assertThat(awsProperties.getAccessKey()).isEqualTo("ACCESS_KEY");
				assertThat(awsProperties.getSecretKey()).isEqualTo("SECRET_KEY");
			});
	}

	@Test
	public void embeddingDisabled() {

		// It is disabled by default
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withConfiguration(AutoConfigurations.of(BedrockCohereEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockCohereEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockCohereEmbeddingModel.class)).isEmpty();
			});

		// Explicitly enable the embedding auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.cohere.embedding.enabled=true")
			.withConfiguration(AutoConfigurations.of(BedrockCohereEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockCohereEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(BedrockCohereEmbeddingModel.class)).isNotEmpty();
			});

		// Explicitly disable the embedding auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.cohere.embedding.enabled=false")
			.withConfiguration(AutoConfigurations.of(BedrockCohereEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockCohereEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockCohereEmbeddingModel.class)).isEmpty();
			});
	}

}
