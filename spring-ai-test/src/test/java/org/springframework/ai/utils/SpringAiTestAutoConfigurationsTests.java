package org.springframework.ai.utils;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Issam El-atif
 */
class SpringAiTestAutoConfigurationsTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	void shouldLoadProvidedConfiguration() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(SimpleConfiguration.class))
			.run(context -> assertThat(context).hasSingleBean(SimpleConfiguration.class));
	}

	@Test
	void shouldIncludeConfigurationsDeclaredInAfterAttribute() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AfterConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(SimpleConfiguration.class);
				assertThat(context).hasSingleBean(AfterConfiguration.class);
			});
	}

	@AutoConfiguration
	static class SimpleConfiguration {

	}

	@AutoConfiguration(after = { SimpleConfiguration.class })
	static class AfterConfiguration {

	}

}
