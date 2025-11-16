package org.springframework.ai.cohere.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link CohereCommonProperties}.
 */
public class CoherePropertiesTests {

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.cohere.base-url=TEST_BASE_URL", "spring.ai.cohere.api-key=abc123",
					"spring.ai.cohere.chat.options.tools[0].function.name=myFunction1",
					"spring.ai.cohere.chat.options.tools[0].function.description=function description",
					"spring.ai.cohere.chat.options.tools[0].function.jsonSchema=" + """
							{
								"type": "object",
								"properties": {
									"location": {
										"type": "string",
										"description": "The city and state e.g. San Francisco, CA"
									},
									"lat": {
										"type": "number",
										"description": "The city latitude"
									},
									"lon": {
										"type": "number",
										"description": "The city longitude"
									},
									"unit": {
										"type": "string",
										"enum": ["c", "f"]
									}
								},
								"required": ["location", "lat", "lon", "unit"]
							}
							""")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, CohereChatAutoConfiguration.class))
			.run(context -> {

				var chatProperties = context.getBean(CohereChatProperties.class);

				var tool = chatProperties.getOptions().getTools().get(0);
				assertThat(tool.getType()).isEqualTo(CohereApi.FunctionTool.Type.FUNCTION);
				var function = tool.getFunction();
				assertThat(function.getName()).isEqualTo("myFunction1");
				assertThat(function.getDescription()).isEqualTo("function description");
				assertThat(function.getParameters()).isNotEmpty();
			});
	}

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner()
				.withPropertyValues("spring.ai.cohere.base-url=TEST_BASE_URL", "spring.ai.cohere.api-key=abc123",
						"spring.ai.cohere.embedding.options.model=MODEL_XYZ")
				.withConfiguration(SpringAiTestAutoConfigurations.of(CohereEmbeddingAutoConfiguration.class))
				.run(context -> {
					var embeddingProperties = context.getBean(CohereEmbeddingProperties.class);
					var connectionProperties = context.getBean(CohereCommonProperties.class);

					assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
					assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

					assertThat(embeddingProperties.getApiKey()).isNull();
					assertThat(embeddingProperties.getBaseUrl()).isEqualTo(CohereCommonProperties.DEFAULT_BASE_URL);

					assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				});
	}

	@Test
	public void embeddingOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues("spring.ai.cohere.base-url=TEST_BASE_URL",
						"spring.ai.cohere.api-key=abc123", "spring.ai.cohere.embedding.base-url=TEST_BASE_URL2",
						"spring.ai.cohere.embedding.api-key=456", "spring.ai.cohere.embedding.options.model=MODEL_XYZ")
				.withConfiguration(SpringAiTestAutoConfigurations.of(CohereEmbeddingAutoConfiguration.class))
				.run(context -> {
					var embeddingProperties = context.getBean(CohereEmbeddingProperties.class);
					var connectionProperties = context.getBean(CohereCommonProperties.class);

					assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
					assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

					assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
					assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

					assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				});
	}

	@Test
	public void embeddingOptionsTest() {

		new ApplicationContextRunner()
				.withPropertyValues("spring.ai.cohere.api-key=API_KEY", "spring.ai.cohere.base-url=TEST_BASE_URL",
						"spring.ai.cohere.embedding.options.model=MODEL_XYZ",
						"spring.ai.cohere.embedding.options.embedding-types[0]=FLOAT",
						"spring.ai.cohere.embedding.options.input-type=search_document",
						"spring.ai.cohere.embedding.options.truncate=END")
				.withConfiguration(SpringAiTestAutoConfigurations.of(CohereEmbeddingAutoConfiguration.class))
				.run(context -> {
					var connectionProperties = context.getBean(CohereCommonProperties.class);
					var embeddingProperties = context.getBean(CohereEmbeddingProperties.class);

					assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
					assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

					assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
					assertThat(embeddingProperties.getOptions().getEmbeddingTypes().get(0).name()).isEqualTo("FLOAT");
					assertThat(embeddingProperties.getOptions().getTruncate().name()).isEqualTo("END");
					assertThat(embeddingProperties.getOptions().getInputType().name()).isEqualTo("SEARCH_DOCUMENT");
				});
	}

}
