package org.springframework.ai.model.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.model.ToolContextCreator;

import java.util.Map;

/**
 * A default implementation of {@link ToolContextCreator} that creates a new instance of
 * {@link ToolContext}.
 */
public class DefaultToolContextCreator implements ToolContextCreator<ToolContext> {

	@Override
	public ToolContext create(final Map<String, Object> context) {
		return new ToolContext(context);
	}

}
