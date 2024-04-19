package org.springframework.ai.chat.prompt.transformer;

/**
 * Responsible for transforming a Prompt. The PromptContext contains the necessary data to
 * make the transformation
 *
 * Implementations may retrieve data and modify the Prompt object in the PromptContext as
 * needed.
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
@FunctionalInterface
public interface PromptTransformer {

	/**
	 * Transforms the given PromptContext.
	 * @param context the PromptContext to transform
	 * @return the transformed PromptContext
	 */
	PromptContext transform(PromptContext context);

}
