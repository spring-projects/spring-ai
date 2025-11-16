package org.springframework.ai.cohere.autoconfigure;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.cohere.chat.CohereChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".*")
public class CohereAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(CohereAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.cohere.apiKey=" + System.getenv("COHERE_API_KEY"));

	@Test
	void generate() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(CohereChatAutoConfiguration.class)).run(context -> {
			CohereChatModel chatModel = context.getBean(CohereChatModel.class);
			String response = chatModel.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

}
