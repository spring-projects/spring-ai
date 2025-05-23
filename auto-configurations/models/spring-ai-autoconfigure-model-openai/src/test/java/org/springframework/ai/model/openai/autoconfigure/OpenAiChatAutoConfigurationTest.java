package org.springframework.ai.model.openai.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatAutoConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"),
					"spring.ai.openai.chat.options.temperature=0.8");

	@Test
	void whenUserDefinesTemperature_thenOverride() {
		runner.withUserConfiguration(UserOptionsConfig.class)
				.run(context -> {
					assertThat(context).hasSingleBean(OpenAiChatModel.class);
					ChatOptions opts = context.getBean(OpenAiChatModel.class).getDefaultOptions();
					assertThat(opts.getTemperature()).isEqualTo(0.42);
				});
	}

	@Test
	void whenNoUserOptions_thenDefaultTemperatureRetained() {
		runner.run(context -> {
			assertThat(context).hasSingleBean(OpenAiChatModel.class);
			ChatOptions opts = context.getBean(OpenAiChatModel.class).getDefaultOptions();
			assertThat(opts.getTemperature()).isEqualTo(0.8);
		});
	}

	@Test
	void whenSearchModel_thenTemperatureOmitted() {
		runner.withPropertyValues("spring.ai.openai.chat.options.model=text-search-abc")
				.run(context -> {
					assertThat(context).hasSingleBean(OpenAiChatModel.class);
					ChatOptions opts = context.getBean(OpenAiChatModel.class).getDefaultOptions();
					assertThat(opts.getTemperature()).isNull();
				});
	}

	@Configuration
	static class UserOptionsConfig {
		@Bean
		public OpenAiChatOptions userOptions() {
			OpenAiChatOptions openAiChatOptions = new OpenAiChatOptions();
			openAiChatOptions.setTemperature(0.42);
			return openAiChatOptions;
		}
	}
}
