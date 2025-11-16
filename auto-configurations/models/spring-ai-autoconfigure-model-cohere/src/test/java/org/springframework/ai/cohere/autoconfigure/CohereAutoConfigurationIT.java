package org.springframework.ai.cohere.autoconfigure;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.cohere.chat.CohereChatModel;
import org.springframework.ai.cohere.embedding.CohereEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".*")
public class CohereAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(CohereAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.cohere.apiKey=" + System.getenv("COHERE_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(CohereChatAutoConfiguration.class));

	@Test
	void generate() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(CohereChatAutoConfiguration.class)).run(context -> {
			CohereChatModel chatModel = context.getBean(CohereChatModel.class);
			String response = chatModel.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void embedding() {
		this.contextRunner
				.withConfiguration(SpringAiTestAutoConfigurations.of(CohereEmbeddingAutoConfiguration.class))
				.run(context -> {
					CohereEmbeddingModel embeddingModel = context.getBean(CohereEmbeddingModel.class);

					EmbeddingResponse embeddingResponse = embeddingModel
							.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
					assertThat(embeddingResponse.getResults()).hasSize(2);
					assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
					assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
					assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
					assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

					assertThat(embeddingModel.dimensions()).isEqualTo(1536);
				});
	}

}
