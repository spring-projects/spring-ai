package org.springframework.ai.chat.model;

import java.util.Map;

/**
 * A functional interface for creating a {@link ToolContext} instance.
 */
public interface ToolContextCreator<Ctx extends ToolContext> {

	/**
	 * Create a new instance of {@link ToolContext} with the provided context map.
	 */
	Ctx create(final Map<String, Object> context);

}
