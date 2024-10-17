/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.ollama.management;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.ai.ollama.BaseOllamaIT;
import org.springframework.ai.ollama.api.OllamaModel;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OllamaModelManager}.
 *
 * @author Thomas Vitale
 */
@Testcontainers
@DisabledIf("isDisabled")
class OllamaModelManagerIT extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.NOMIC_EMBED_TEXT.getName();

	static OllamaModelManager modelManager;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		var ollamaApi = buildOllamaApiWithModel(MODEL);
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
	public void pullAndDeleteModel() {
		var model = "all-minilm";
		modelManager.pullModel(model, PullModelStrategy.WHEN_MISSING);
		var isModelAvailable = modelManager.isModelAvailable(model);
		assertThat(isModelAvailable).isTrue();

		modelManager.deleteModel(model);
		isModelAvailable = modelManager.isModelAvailable(model);
		assertThat(isModelAvailable).isFalse();

		model = "all-minilm:latest";
		modelManager.pullModel(model, PullModelStrategy.WHEN_MISSING);
		isModelAvailable = modelManager.isModelAvailable(model);
		assertThat(isModelAvailable).isTrue();

		modelManager.deleteModel(model);
		isModelAvailable = modelManager.isModelAvailable(model);
		assertThat(isModelAvailable).isFalse();
	}

}
