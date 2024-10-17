package org.springframework.ai.autoconfigure.ollama;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.testcontainers.ollama.OllamaContainer;

public class BaseOllamaIT {

	// Toggle for running tests locally on native Ollama for a faster feedback loop.
	private static final boolean useTestcontainers = true;

	public static final OllamaContainer ollamaContainer;

	static {
		ollamaContainer = new OllamaContainer(OllamaImage.IMAGE).withReuse(true);
		ollamaContainer.start();
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

	public static String buildConnectionWithModel(String model) {
		var baseUrl = "http://localhost:11434";
		if (useTestcontainers) {
			baseUrl = ollamaContainer.getEndpoint();
		}
		var ollamaModelManager = new OllamaModelManager(new OllamaApi(baseUrl));
		ollamaModelManager.pullModel(model, PullModelStrategy.WHEN_MISSING);
		return baseUrl;
	}

}
