package org.springframework.ai.cohere.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.cohere.chat.CohereChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Cohere auto-configurations conditional enabling of models.
 *
 * @author Ricken Bazolo
 */
public class CohereModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.cohere.apiKey=" + System.getenv("COHERE_API_KEY"));

	@Test
	void chatModelActivation() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(CohereChatAutoConfiguration.class))
				.run(context -> {
					assertThat(context.getBeansOfType(CohereChatProperties.class)).isNotEmpty();
					assertThat(context.getBeansOfType(CohereChatModel.class)).isNotEmpty();
				});

		this.contextRunner.withConfiguration(AutoConfigurations.of(CohereChatAutoConfiguration.class))
				.withPropertyValues("spring.ai.model.chat=none")
				.run(context -> {
					assertThat(context.getBeansOfType(CohereChatProperties.class)).isEmpty();
					assertThat(context.getBeansOfType(CohereChatModel.class)).isEmpty();
				});
	}
}
