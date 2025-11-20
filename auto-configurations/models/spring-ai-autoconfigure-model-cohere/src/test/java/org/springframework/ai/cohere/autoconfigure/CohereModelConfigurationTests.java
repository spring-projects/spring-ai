package org.springframework.ai.cohere.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.cohere.chat.CohereChatModel;
import org.springframework.ai.cohere.embedding.CohereEmbeddingModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Cohere auto-configurations conditional enabling of models.
 *
 * @author Ricken Bazolo
 */
public class CohereModelConfigurationTests {

	private final ApplicationContextRunner chatContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.cohere.apiKey=" + System.getenv("COHERE_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(CohereChatAutoConfiguration.class));

	private final ApplicationContextRunner embeddingContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.cohere.apiKey=" + System.getenv("COHERE_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(CohereEmbeddingAutoConfiguration.class));

	@Test
	void chatModelActivation() {
		this.chatContextRunner.run(context -> {
			assertThat(context.getBeansOfType(CohereChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(CohereChatModel.class)).isNotEmpty();
			assertThat(context.getBeansOfType(CohereEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isEmpty();
		});

		this.chatContextRunner.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(CohereChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(CohereChatModel.class)).isEmpty();
			});

		this.chatContextRunner.withPropertyValues("spring.ai.model.chat=cohere", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(CohereChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(CohereChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(CohereEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isEmpty();
			});
	}

	@Test
	void embeddingModelActivation() {
		this.embeddingContextRunner
			.run(context -> assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isNotEmpty());

		this.embeddingContextRunner.withPropertyValues("spring.ai.model.embedding=none").run(context -> {
			assertThat(context.getBeansOfType(CohereEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isEmpty();
		});

		this.embeddingContextRunner.withPropertyValues("spring.ai.model.embedding=cohere").run(context -> {
			assertThat(context.getBeansOfType(CohereEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isNotEmpty();
		});
	}

}
