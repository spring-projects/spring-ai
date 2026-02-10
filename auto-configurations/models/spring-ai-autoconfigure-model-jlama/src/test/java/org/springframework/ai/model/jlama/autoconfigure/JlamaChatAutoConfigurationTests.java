/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.model.jlama.autoconfigure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.jlama.JlamaChatModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JlamaChatAutoConfiguration}.
 *
 * @author chabinhwang
 */
class JlamaChatAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(SpringAiTestAutoConfigurations.of(JlamaChatAutoConfiguration.class));

	@Test
	void autoConfigurationWithAllProperties(@TempDir Path tempDir) throws IOException {
		// Create a dummy model file
		Path modelFile = tempDir.resolve("test-model.jlama");
		Files.write(modelFile, "dummy model content".getBytes());

		// Skip this test if Vector API is not available
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");
		}
		catch (ClassNotFoundException e) {
			assertThat(true).as("Vector API not available, skipping test").isTrue();
			return;
		}

		this.contextRunner
			.withPropertyValues("spring.ai.jlama.model=" + modelFile.toString(),
					"spring.ai.jlama.working-directory=" + tempDir.toString(),
					"spring.ai.jlama.options.temperature=0.9", "spring.ai.jlama.options.max-tokens=1024",
					"spring.ai.jlama.options.top-k=50", "spring.ai.jlama.options.top-p=0.95")
			.run(context -> {
				assertThat(context).hasSingleBean(JlamaChatProperties.class);
				assertThat(context).hasSingleBean(JlamaChatModel.class);

				var properties = context.getBean(JlamaChatProperties.class);
				assertThat(properties.getModel()).isEqualTo(modelFile.toString());
				assertThat(properties.getWorkingDirectory()).isEqualTo(tempDir.toString());
				assertThat(properties.getOptions().getTemperature()).isEqualTo(0.9);
				assertThat(properties.getOptions().getMaxTokens()).isEqualTo(1024);
				assertThat(properties.getOptions().getTopK()).isEqualTo(50);
				assertThat(properties.getOptions().getTopP()).isEqualTo(0.95);
			});
	}

	@Test
	void autoConfigurationWithMinimalProperties(@TempDir Path tempDir) throws IOException {
		// Create a dummy model file
		Path modelFile = tempDir.resolve("test-model.jlama");
		Files.write(modelFile, "dummy model content".getBytes());

		// Skip this test if Vector API is not available
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");
		}
		catch (ClassNotFoundException e) {
			assertThat(true).as("Vector API not available, skipping test").isTrue();
			return;
		}

		this.contextRunner.withPropertyValues("spring.ai.jlama.model=" + modelFile.toString()).run(context -> {
			assertThat(context).hasSingleBean(JlamaChatProperties.class);
			assertThat(context).hasSingleBean(JlamaChatModel.class);

			var properties = context.getBean(JlamaChatProperties.class);
			assertThat(properties.getModel()).isEqualTo(modelFile.toString());
			assertThat(properties.getWorkingDirectory()).isNull();
		});
	}

	@Test
	void autoConfigurationWithWorkingDirectory(@TempDir Path tempDir) throws IOException {
		// Create working directory and model file
		Path workingDir = tempDir.resolve("working");
		Files.createDirectories(workingDir);
		Path modelFile = workingDir.resolve("model.jlama");
		Files.write(modelFile, "dummy model content".getBytes());

		// Skip this test if Vector API is not available
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");
		}
		catch (ClassNotFoundException e) {
			assertThat(true).as("Vector API not available, skipping test").isTrue();
			return;
		}

		this.contextRunner
			.withPropertyValues("spring.ai.jlama.model=" + modelFile.toString(),
					"spring.ai.jlama.working-directory=" + workingDir.toString())
			.run(context -> {
				assertThat(context).hasSingleBean(JlamaChatModel.class);

				var properties = context.getBean(JlamaChatProperties.class);
				assertThat(properties.getWorkingDirectory()).isEqualTo(workingDir.toString());
			});
	}

	@Test
	void autoConfigurationDisabledWhenClassNotPresent() {
		this.contextRunner.withPropertyValues("spring.ai.jlama.model=test-model")
			.withClassLoader(new FilteredClassLoader(JlamaChatModel.class))
			.run(context -> {
				assertThat(context).doesNotHaveBean(JlamaChatAutoConfiguration.class);
				assertThat(context).doesNotHaveBean(JlamaChatModel.class);
			});
	}

	@Test
	void autoConfigurationDisabledByProperty() {
		this.contextRunner.withPropertyValues("spring.ai.model.chat=none", "spring.ai.jlama.model=test-model")
			.run(context -> {
				assertThat(context).doesNotHaveBean(JlamaChatAutoConfiguration.class);
				assertThat(context).doesNotHaveBean(JlamaChatModel.class);
			});
	}

	@Test
	void optionsAreConfiguredFromProperties(@TempDir Path tempDir) throws IOException {
		// Create a dummy model file
		Path modelFile = tempDir.resolve("test-model.jlama");
		Files.write(modelFile, "dummy model content".getBytes());

		// Skip this test if Vector API is not available
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");
		}
		catch (ClassNotFoundException e) {
			assertThat(true).as("Vector API not available, skipping test").isTrue();
			return;
		}

		this.contextRunner
			.withPropertyValues("spring.ai.jlama.model=" + modelFile.toString(),
					"spring.ai.jlama.options.temperature=0.8", "spring.ai.jlama.options.max-tokens=512",
					"spring.ai.jlama.options.top-k=40", "spring.ai.jlama.options.top-p=0.9",
					"spring.ai.jlama.options.frequency-penalty=0.5", "spring.ai.jlama.options.presence-penalty=0.6",
					"spring.ai.jlama.options.seed=42")
			.run(context -> {
				var properties = context.getBean(JlamaChatProperties.class);
				var options = properties.getOptions();

				assertThat(options.getTemperature()).isEqualTo(0.8);
				assertThat(options.getMaxTokens()).isEqualTo(512);
				assertThat(options.getTopK()).isEqualTo(40);
				assertThat(options.getTopP()).isEqualTo(0.9);
				assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
				assertThat(options.getPresencePenalty()).isEqualTo(0.6);
				assertThat(options.getSeed()).isEqualTo(42L);
			});
	}

	/**
	 * FilteredClassLoader that prevents certain classes from being loaded.
	 */
	private static class FilteredClassLoader extends ClassLoader {

		private final Class<?> filteredClass;

		FilteredClassLoader(Class<?> filteredClass) {
			super(JlamaChatAutoConfigurationTests.class.getClassLoader());
			this.filteredClass = filteredClass;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (name.equals(this.filteredClass.getName())) {
				throw new ClassNotFoundException();
			}
			return super.loadClass(name, resolve);
		}

	}

}
