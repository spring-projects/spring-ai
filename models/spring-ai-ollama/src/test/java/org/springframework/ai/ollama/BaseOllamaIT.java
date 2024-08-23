package org.springframework.ai.ollama;

import org.testcontainers.ollama.OllamaContainer;

public class BaseOllamaIT {

	public static final OllamaContainer ollamaContainer;

	static {
		ollamaContainer = new OllamaContainer(OllamaImage.DEFAULT_IMAGE).withReuse(true);
		ollamaContainer.start();
	}

	/**
	 * Change the value to false in order to run multiple Ollama IT tests locally reusing
	 * the same container image Also add the entry
	 *
	 * testcontainers.reuse.enable=true
	 *
	 * to the file .testcontainers.properties located in your home directory
	 */
	public static boolean isDisabled() {
		return true;
	}

}
