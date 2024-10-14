package org.springframework.ai.autoconfigure.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.ollama.OllamaContainer;

import java.io.IOException;

public class BaseOllamaIT {

	private static final Logger logger = LoggerFactory.getLogger(BaseOllamaIT.class);

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
		return true;
	}

	public static String buildConnectionWithModel(String model) throws IOException, InterruptedException {
		var baseUrl = "http://localhost:11434";
		if (useTestcontainers) {
			baseUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);

			logger.info("Start pulling the '{}' model. The operation can take several minutes...", model);
			ollamaContainer.execInContainer("ollama", "pull", model);
			logger.info("Completed pulling the '{}' model", model);
		}
		return baseUrl;
	}

}
