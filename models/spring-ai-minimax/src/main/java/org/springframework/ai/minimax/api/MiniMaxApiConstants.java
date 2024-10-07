package org.springframework.ai.minimax.api;

import org.springframework.ai.observation.conventions.AiProvider;

/**
 * Common value constants for MiniMax api.
 *
 * @author Piotr Olaszewski
 * @since 1.0.0 M2
 */
public final class MiniMaxApiConstants {

	public static final String DEFAULT_BASE_URL = "https://api.minimax.chat";

	public static final String TOOL_CALL_FUNCTION_TYPE = "function";

	public static final String PROVIDER_NAME = AiProvider.MINIMAX.value();

}
