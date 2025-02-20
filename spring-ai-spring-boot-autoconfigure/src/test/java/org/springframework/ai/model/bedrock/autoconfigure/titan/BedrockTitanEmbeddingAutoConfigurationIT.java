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

package org.springframework.ai.model.bedrock.autoconfigure.titan;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionProperties;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockTestUtils;
import org.springframework.ai.model.bedrock.autoconfigure.RequiresAwsCredentials;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel.InputType;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Mark Pollack
 * @since 1.0.0
 */
@RequiresAwsCredentials
public class BedrockTitanEmbeddingAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = BedrockTestUtils.getContextRunner()
		.withPropertyValues("spring.ai.bedrock.titan.embedding.enabled=true",
				"spring.ai.bedrock.aws.access-key=" + System.getenv("AWS_ACCESS_KEY_ID"),
				"spring.ai.bedrock.aws.secret-key=" + System.getenv("AWS_SECRET_ACCESS_KEY"),
				"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
				"spring.ai.bedrock.titan.embedding.model=" + TitanEmbeddingModel.TITAN_EMBED_IMAGE_V1.id())
		.withConfiguration(AutoConfigurations.of(BedrockTitanEmbeddingAutoConfiguration.class));

	@Test
	public void singleTextEmbedding() {
		this.contextRunner.withPropertyValues("spring.ai.bedrock.titan.embedding.inputType=TEXT").run(context -> {
			BedrockTitanEmbeddingModel embeddingModel = context.getBean(BedrockTitanEmbeddingModel.class);
			assertThat(embeddingModel).isNotNull();
			EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of("Hello World"));
			assertThat(embeddingResponse.getResults()).hasSize(1);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingModel.dimensions()).isEqualTo(1024);
		});
	}

	@Test
	public void singleImageEmbedding() {
		this.contextRunner.withPropertyValues("spring.ai.bedrock.titan.embedding.inputType=IMAGE").run(context -> {
			BedrockTitanEmbeddingModel embeddingModel = context.getBean(BedrockTitanEmbeddingModel.class);
			assertThat(embeddingModel).isNotNull();

			byte[] image = new DefaultResourceLoader().getResource("classpath:/spring_framework.png")
				.getContentAsByteArray();

			var base64Image = Base64.getEncoder().encodeToString(image);

			EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(base64Image));

			assertThat(embeddingResponse.getResults()).hasSize(1);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingModel.dimensions()).isEqualTo(1024);
		});
	}

	@Test
	public void propertiesTest() {

		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.titan.embedding.enabled=true",
					"spring.ai.bedrock.aws.access-key=ACCESS_KEY", "spring.ai.bedrock.aws.secret-key=SECRET_KEY",
					"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
					"spring.ai.bedrock.titan.embedding.model=MODEL_XYZ",
					"spring.ai.bedrock.titan.embedding.inputType=TEXT")
			.withConfiguration(AutoConfigurations.of(BedrockTitanEmbeddingAutoConfiguration.class))
			.run(context -> {
				var properties = context.getBean(BedrockTitanEmbeddingProperties.class);
				var awsProperties = context.getBean(BedrockAwsConnectionProperties.class);

				assertThat(properties.isEnabled()).isTrue();
				assertThat(awsProperties.getRegion()).isEqualTo(Region.US_EAST_1.id());
				assertThat(properties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(properties.getInputType()).isEqualTo(InputType.TEXT);

				assertThat(awsProperties.getAccessKey()).isEqualTo("ACCESS_KEY");
				assertThat(awsProperties.getSecretKey()).isEqualTo("SECRET_KEY");
			});
	}

	@Test
	public void embeddingDisabled() {

		// It is disabled by default
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withConfiguration(AutoConfigurations.of(BedrockTitanEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockTitanEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockTitanEmbeddingModel.class)).isEmpty();
			});

		// Explicitly enable the embedding auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.titan.embedding.enabled=true")
			.withConfiguration(AutoConfigurations.of(BedrockTitanEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockTitanEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(BedrockTitanEmbeddingModel.class)).isNotEmpty();
			});

		// Explicitly disable the embedding auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.titan.embedding.enabled=false")
			.withConfiguration(AutoConfigurations.of(BedrockTitanEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockTitanEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockTitanEmbeddingModel.class)).isEmpty();
			});
	}

}
