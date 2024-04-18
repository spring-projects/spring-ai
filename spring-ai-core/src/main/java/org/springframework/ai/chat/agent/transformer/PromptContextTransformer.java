package org.springframework.ai.chat.agent.transformer;

import org.springframework.ai.chat.agent.PromptContext;

/**
 * Transforms the PromptContext. Implementations may retrieve data and modify the Prompt
 * as needed.
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
@FunctionalInterface
public interface PromptContextTransformer {

	/**
	 * Transforms the given PromptContext.
	 * @param context the PromptContext to transform
	 * @return the transformed PromptContext
	 */
	PromptContext transform(PromptContext context);

}
