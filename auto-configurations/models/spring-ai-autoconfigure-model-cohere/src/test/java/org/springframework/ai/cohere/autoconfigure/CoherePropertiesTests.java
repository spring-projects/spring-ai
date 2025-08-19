package org.springframework.ai.cohere.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
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

		new ApplicationContextRunner().withPropertyValues(
				 "spring.ai.cohere.base-url=TEST_BASE_URL",
						"spring.ai.cohere.api-key=abc123",
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
}
