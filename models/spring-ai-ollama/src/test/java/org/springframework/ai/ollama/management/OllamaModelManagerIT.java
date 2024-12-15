/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.ollama.management;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.ai.ollama.BaseOllamaIT;
import org.springframework.ai.ollama.api.OllamaModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OllamaModelManager}.
 *
 * @author Thomas Vitale
 */
class OllamaModelManagerIT extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.NOMIC_EMBED_TEXT.getName();

	static OllamaModelManager modelManager;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		var ollamaApi = initializeOllama(MODEL);
		modelManager = new OllamaModelManager(ollamaApi);
	}

	@Test
	public void whenModelAvailableReturnTrue() {
		var isModelAvailable = modelManager.isModelAvailable(MODEL);
		assertThat(isModelAvailable).isTrue();

		isModelAvailable = modelManager.isModelAvailable(MODEL + ":latest");
		assertThat(isModelAvailable).isTrue();
	}

	@Test
	public void whenModelNotAvailableReturnFalse() {
		var isModelAvailable = modelManager.isModelAvailable("aleph");
		assertThat(isModelAvailable).isFalse();
	}

	@Test
	@Disabled("This test is brittle and fails often in CI")
	public void pullAndDeleteModelFromOllama() {
		// Pull model with explicit version.
		var modelWithExplicitVersion = "all-minilm:33m";
		modelManager.deleteModel(modelWithExplicitVersion);
		modelManager.pullModel(modelWithExplicitVersion, PullModelStrategy.WHEN_MISSING);
		var isModelWithExplicitVersionAvailable = modelManager.isModelAvailable(modelWithExplicitVersion);
		assertThat(isModelWithExplicitVersionAvailable).isTrue();

		// Pull same model without version, which should pull the "latest" version.
		var modelWithoutVersion = "all-minilm";
		modelManager.deleteModel(modelWithoutVersion);
		var isModelWithoutVersionAvailable = modelManager.isModelAvailable(modelWithoutVersion);
		assertThat(isModelWithoutVersionAvailable).isFalse();
		isModelWithExplicitVersionAvailable = modelManager.isModelAvailable(modelWithExplicitVersion);
		assertThat(isModelWithExplicitVersionAvailable).isTrue();

		modelManager.pullModel(modelWithoutVersion, PullModelStrategy.WHEN_MISSING);
		isModelWithoutVersionAvailable = modelManager.isModelAvailable(modelWithoutVersion);
		assertThat(isModelWithoutVersionAvailable).isTrue();

		// Pull model with ":latest" suffix, with has the same effect as pulling the model
		// without version.
		var modelWithLatestVersion = "all-minilm:latest";
		var isModelWithLatestVersionAvailable = modelManager.isModelAvailable(modelWithLatestVersion);
		assertThat(isModelWithLatestVersionAvailable).isTrue();

		// Final clean-up.
		modelManager.deleteModel(modelWithExplicitVersion);
		isModelWithExplicitVersionAvailable = modelManager.isModelAvailable(modelWithExplicitVersion);
		assertThat(isModelWithExplicitVersionAvailable).isFalse();

		modelManager.deleteModel(modelWithLatestVersion);
		isModelWithLatestVersionAvailable = modelManager.isModelAvailable(modelWithLatestVersion);
		assertThat(isModelWithLatestVersionAvailable).isFalse();
	}

	@Disabled
	@Test
	public void pullAndDeleteModelFromHuggingFace() {
		// Pull model with explicit version.
		var modelWithExplicitVersion = "hf.co/SanctumAI/Llama-3.2-1B-Instruct-GGUF:Q3_K_S";
		modelManager.deleteModel(modelWithExplicitVersion);
		modelManager.pullModel(modelWithExplicitVersion, PullModelStrategy.WHEN_MISSING);
		var isModelWithExplicitVersionAvailable = modelManager.isModelAvailable(modelWithExplicitVersion);
		assertThat(isModelWithExplicitVersionAvailable).isTrue();

		// Pull same model without version, which should pull the "latest" version.
		var modelWithoutVersion = "hf.co/SanctumAI/Llama-3.2-1B-Instruct-GGUF";
		modelManager.deleteModel(modelWithoutVersion);
		var isModelWithoutVersionAvailable = modelManager.isModelAvailable(modelWithoutVersion);
		assertThat(isModelWithoutVersionAvailable).isFalse();
		isModelWithExplicitVersionAvailable = modelManager.isModelAvailable(modelWithExplicitVersion);
		assertThat(isModelWithExplicitVersionAvailable).isTrue();

		modelManager.pullModel(modelWithoutVersion, PullModelStrategy.WHEN_MISSING);
		isModelWithoutVersionAvailable = modelManager.isModelAvailable(modelWithoutVersion);
		assertThat(isModelWithoutVersionAvailable).isTrue();

		// Pull model with ":latest" suffix, with has the same effect as pulling the model
		// without version.
		var modelWithLatestVersion = "hf.co/SanctumAI/Llama-3.2-1B-Instruct-GGUF:latest";
		var isModelWithLatestVersionAvailable = modelManager.isModelAvailable(modelWithLatestVersion);
		assertThat(isModelWithLatestVersionAvailable).isTrue();

		// Final clean-up.
		modelManager.deleteModel(modelWithExplicitVersion);
		isModelWithExplicitVersionAvailable = modelManager.isModelAvailable(modelWithExplicitVersion);
		assertThat(isModelWithExplicitVersionAvailable).isFalse();

		modelManager.deleteModel(modelWithLatestVersion);
		isModelWithLatestVersionAvailable = modelManager.isModelAvailable(modelWithLatestVersion);
		assertThat(isModelWithLatestVersionAvailable).isFalse();
	}

	@Test
	@Disabled("This test is brittle and fails often in CI")
	public void pullAdditionalModels() {
		var model = "all-minilm";
		var isModelAvailable = modelManager.isModelAvailable(model);
		assertThat(isModelAvailable).isFalse();

		new OllamaModelManager(getOllamaApi(),
				new ModelManagementOptions(PullModelStrategy.WHEN_MISSING, List.of(model), Duration.ofMinutes(5), 0));

		isModelAvailable = modelManager.isModelAvailable(model);
		assertThat(isModelAvailable).isTrue();

		modelManager.deleteModel(model);
		isModelAvailable = modelManager.isModelAvailable(model);
		assertThat(isModelAvailable).isFalse();
	}

}
