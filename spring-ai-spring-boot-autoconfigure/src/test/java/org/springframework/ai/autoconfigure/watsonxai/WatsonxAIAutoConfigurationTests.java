package org.springframework.ai.autoconfigure.watsonxai;

import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class WatsonxAIAutoConfigurationTests {

	@Test
	public void propertiesTest() {
		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
                        "spring.ai.watsonx.ai.base-url=TEST_BASE_URL",
                        "spring.ai.watsonx.ai.stream-endpoint=generation/stream?version=2023-05-29",
                        "spring.ai.watsonx.ai.text-endpoint=generation/text?version=2023-05-29",
                        "spring.ai.watsonx.ai.projectId=1",
                        "spring.ai.watsonx.ai.IAMToken=123456")
                // @formatter:on
			.withConfiguration(AutoConfigurations.of(WatsonxAIAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(WatsonxAIConnectionProperties.class);
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getStreamEndpoint()).isEqualTo("generation/stream?version=2023-05-29");
				assertThat(connectionProperties.getTextEndpoint()).isEqualTo("generation/text?version=2023-05-29");
				assertThat(connectionProperties.getProjectId()).isEqualTo("1");
				assertThat(connectionProperties.getIAMToken()).isEqualTo("123456");
			});
	}

}