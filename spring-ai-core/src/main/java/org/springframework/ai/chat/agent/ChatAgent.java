package org.springframework.ai.chat.agent;

import org.springframework.ai.chat.transformer.PromptContext;

/**
 * A ChatAgent encapsulates common AI workflows such as Retrieval Augmented Generation.
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
public interface ChatAgent {

	/**
	 * Call the chat agent to execute a workflow
	 * @param promptContext A shared data structure that can be used in components that
	 * implement the workflow. Contains the initial Prompt and a conversation ID at the
	 * start of the workflow.
	 * @return the AgentResponse that contains the ChatResponse and the latest
	 * PromptContext
	 */
	AgentResponse call(PromptContext promptContext);

}
