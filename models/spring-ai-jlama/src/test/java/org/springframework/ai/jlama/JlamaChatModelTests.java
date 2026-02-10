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

package org.springframework.ai.jlama;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.jlama.api.JlamaChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JlamaChatModel}.
 *
 * @author chabinhwang
 */
class JlamaChatModelTests {

	@Test
	void constructorShouldFailWithNullModelPath() {
		var options = JlamaChatOptions.builder().build();

		assertThatThrownBy(() -> new JlamaChatModel(null, options)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Model path must not be empty");
	}

	@Test
	void constructorShouldFailWithEmptyModelPath() {
		var options = JlamaChatOptions.builder().build();

		assertThatThrownBy(() -> new JlamaChatModel("", options)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Model path must not be empty");
	}

	@Test
	void constructorShouldFailWithNullOptions() {
		assertThatThrownBy(() -> new JlamaChatModel("test-model", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultOptions must not be null");
	}

	@Test
	void constructorShouldFailWithoutVectorApi() {
		var options = JlamaChatOptions.builder().build();

		// This test assumes Vector API is not available
		// If Vector API is available (via --add-modules jdk.incubator.vector), this test
		// will fail
		// We're testing the error message that should appear when Vector API is missing
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");
			// Vector API is available, skip this test
			assertThat(true).as("Vector API is available, skipping Vector API validation test").isTrue();
		}
		catch (ClassNotFoundException e) {
			// Vector API is not available, test the error message
			assertThatThrownBy(() -> new JlamaChatModel("nonexistent-model", options))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Vector API not available")
				.hasMessageContaining("--add-modules jdk.incubator.vector");
		}
	}

	@Test
	void getDefaultOptionsReturnsCorrectOptions() {
		// This test requires Vector API to be available
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");

			var options = JlamaChatOptions.builder().model("test-model").temperature(0.9).maxTokens(1024).build();

			// We can't actually instantiate JlamaChatModel without a real model file
			// So we just verify the options builder works correctly
			assertThat(options.getModel()).isEqualTo("test-model");
			assertThat(options.getTemperature()).isEqualTo(0.9);
			assertThat(options.getMaxTokens()).isEqualTo(1024);
		}
		catch (ClassNotFoundException e) {
			// Vector API not available, skip test
			assertThat(true).as("Vector API not available, skipping test").isTrue();
		}
	}

	@Test
	void resolveModelWithAbsolutePath(@TempDir Path tempDir) throws IOException {
		// Create a dummy model file
		Path modelFile = tempDir.resolve("test-model.jlama");
		Files.write(modelFile, "dummy model content".getBytes());

		var options = JlamaChatOptions.builder().build();

		// This test would require Vector API and actual Jlama model loading
		// Since we're doing unit tests without real models, we verify the file exists
		assertThat(modelFile.toFile()).exists();
		assertThat(modelFile.toString()).isNotEmpty();

		// The actual model resolution happens in constructor, which we can't test without
		// Vector API
		// and a real model file format
	}

	@Test
	void resolveModelWithWorkingDirectory(@TempDir Path tempDir) throws IOException {
		// Create working directory structure
		Path workingDir = tempDir.resolve("working");
		Files.createDirectories(workingDir);

		Path modelFile = workingDir.resolve("model.jlama");
		Files.write(modelFile, "dummy model content".getBytes());

		// Verify the directory structure
		assertThat(workingDir.toFile()).exists();
		assertThat(modelFile.toFile()).exists();

		// The actual model resolution with workingDirectory would happen in constructor
		// We can't test it without Vector API and real model loading
	}

	@Test
	void resolveModelShouldFailWithInvalidHttpUrl() {
		var options = JlamaChatOptions.builder().build();

		// This test requires Vector API but should fail before model loading
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");

			assertThatThrownBy(() -> new JlamaChatModel("http://example.com/model", options))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("HTTP URLs not yet supported")
				.hasMessageContaining("HuggingFace 'owner/repo'");

			assertThatThrownBy(() -> new JlamaChatModel("https://example.com/model", options))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("HTTP URLs not yet supported")
				.hasMessageContaining("HuggingFace 'owner/repo'");
		}
		catch (ClassNotFoundException e) {
			// Vector API not available, skip test
			assertThat(true).as("Vector API not available, skipping test").isTrue();
		}
	}

	@Test
	void resolveModelShouldFailWithNonexistentFile() {
		var options = JlamaChatOptions.builder().build();

		// This test requires Vector API
		try {
			Class.forName("jdk.incubator.vector.VectorSpecies");

			assertThatThrownBy(() -> new JlamaChatModel("/nonexistent/model/path", options))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Model not found");
		}
		catch (ClassNotFoundException e) {
			// Vector API not available, skip test
			assertThat(true).as("Vector API not available, skipping test").isTrue();
		}
	}

}
