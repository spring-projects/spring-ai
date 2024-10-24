package org.springframework.ai.ollama;

import java.time.Duration;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.util.StringUtils;
import org.testcontainers.ollama.OllamaContainer;

public class BaseOllamaIT {

	// Toggle for running tests locally on native Ollama for a faster feedback loop.
	private static final boolean useTestcontainers = true;

	public static OllamaContainer ollamaContainer;

	static {
		if (useTestcontainers) {
			ollamaContainer = new OllamaContainer(OllamaImage.DEFAULT_IMAGE).withReuse(true);
			ollamaContainer.start();
		}
	}

	/**
	 * Change the return value to false in order to run multiple Ollama IT tests locally
	 * reusing the same container image.
	 *
	 * Also, add the entry
	 *
	 * testcontainers.reuse.enable=true
	 *
	 * to the file ".testcontainers.properties" located in your home directory
	 */
	public static boolean isDisabled() {
		return false;
	}

	public static OllamaApi buildOllamaApi() {
		return buildOllamaApiWithModel(null);
	}

	public static OllamaApi buildOllamaApiWithModel(String model) {
		var baseUrl = "http://localhost:11434";
		if (useTestcontainers) {
			baseUrl = ollamaContainer.getEndpoint();
		}
		var ollamaApi = new OllamaApi(baseUrl);

		if (StringUtils.hasText(model)) {
			ensureModelIsPresent(ollamaApi, model);
		}

		return ollamaApi;
	}

	public static void ensureModelIsPresent(OllamaApi ollamaApi, String model) {
		var modelManagementOptions = ModelManagementOptions.builder()
			.withMaxRetries(2)
			.withTimeout(Duration.ofMinutes(10))
			.build();
		var ollamaModelManager = new OllamaModelManager(ollamaApi, modelManagementOptions);
		ollamaModelManager.pullModel(model, PullModelStrategy.WHEN_MISSING);
	}

}
