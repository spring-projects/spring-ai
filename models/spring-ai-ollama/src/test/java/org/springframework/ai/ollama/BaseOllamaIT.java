package org.springframework.ai.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.api.OllamaApi;
import org.testcontainers.ollama.OllamaContainer;

public class BaseOllamaIT {

	private static final Logger logger = LoggerFactory.getLogger(BaseOllamaIT.class);

	// Toggle for running tests locally on native Ollama for a faster feedback loop.
	private static final boolean useTestcontainers = true;

	public static final OllamaContainer ollamaContainer;

	static {
		ollamaContainer = new OllamaContainer(OllamaImage.DEFAULT_IMAGE).withReuse(true);
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
		return true;
	}

	public static OllamaApi buildOllamaApiWithModel(String model) {
		var baseUrl = "http://localhost:11434";
		if (useTestcontainers) {
			baseUrl = ollamaContainer.getEndpoint();
		}
		var ollamaApi = new OllamaApi(baseUrl);

		ensureModelIsPresent(ollamaApi, model);

		return ollamaApi;
	}

	public static void ensureModelIsPresent(OllamaApi ollamaApi, String model) {
		logger.info("Start pulling the '{}' model. The operation can take several minutes...", model);
		ollamaApi.pullModel(new OllamaApi.PullModelRequest(model));
		logger.info("Completed pulling the '{}' model", model);
	}

}
