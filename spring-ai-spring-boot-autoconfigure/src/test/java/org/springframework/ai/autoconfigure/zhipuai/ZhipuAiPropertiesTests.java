package org.springframework.ai.autoconfigure.zhipuai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link ZhipuAiCommonProperties}, {@link ZhipuAiEmbeddingProperties}.
 *
 * @author Ricken Bazolo
 */
public class ZhipuAiPropertiesTests {

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.base-url=TEST_BASE_URL", "spring.ai.zhipuai.api-key=abc123",
					"spring.ai.zhipuai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, ZhipuAiAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(ZhipuAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(ZhipuAiCommonProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isNull();
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo(ZhipuAiCommonProperties.DEFAULT_BASE_URL);

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void embeddingOverrideConnectionProperties() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.zhipuai.base-url=TEST_BASE_URL", "spring.ai.zhipuai.api-key=abc123",
					"spring.ai.zhipuai.embedding.base-url=TEST_BASE_URL2", "spring.ai.zhipuai.embedding.api-key=456",
					"spring.ai.zhipuai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, ZhipuAiAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(ZhipuAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(ZhipuAiCommonProperties.class);

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
			.withPropertyValues("spring.ai.zhipuai.api-key=API_KEY", "spring.ai.zhipuai.base-url=TEST_BASE_URL",

					"spring.ai.zhipuai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, ZhipuAiAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(ZhipuAiCommonProperties.class);
				var embeddingProperties = context.getBean(ZhipuAiEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

}
