package org.springframework.ai.openai.api.common;

import org.springframework.ai.observation.conventions.AiProvider;

/**
 * Common value constants for OpenAI api.
 *
 * @author Piotr Olaszewski
 * @author Thomas Vitale
 * @since 1.0.0 M2
 */
public final class OpenAiApiConstants {

	public static final String DEFAULT_BASE_URL = "https://api.openai.com";

	public static final String PROVIDER_NAME = AiProvider.OPENAI.value();

}
