package org.springframework.ai.autoconfigure.observation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Spring AI observations.
 *
 * @author Thomas Vitale
 */
@ConfigurationProperties(AiObservationProperties.CONFIG_PREFIX)
public class AiObservationProperties {

	public static final String CONFIG_PREFIX = "spring.ai.observations";

	/**
	 * Whether to include the completion content in the observations.
	 */
	private boolean includeCompletion = false;

	/**
	 * Whether to include the prompt content in the observations.
	 */
	private boolean includePrompt = false;

	public boolean isIncludeCompletion() {
		return includeCompletion;
	}

	public void setIncludeCompletion(boolean includeCompletion) {
		this.includeCompletion = includeCompletion;
	}

	public boolean isIncludePrompt() {
		return includePrompt;
	}

	public void setIncludePrompt(boolean includePrompt) {
		this.includePrompt = includePrompt;
	}

}
