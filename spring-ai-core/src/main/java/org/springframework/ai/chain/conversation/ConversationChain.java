package org.springframework.ai.chain.conversation;

import org.springframework.ai.chain.AiChain;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.memory.ConversationBufferMemory;
import org.springframework.ai.parser.OutputParser;
import org.springframework.ai.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * Chain for conversational interaction, maintaining context in memory.
 *
 * @author Craig Walls
 */
public class ConversationChain extends AiChain implements DefaultTemplate {

	private String inputKey = "input";

	public ConversationChain(AiClient aiClient, String outputKey, OutputParser outputParser) {
		this(aiClient, DEFAULT_PROMPT_TEMPLATE, outputKey, outputParser);
	}
	public ConversationChain(AiClient aiClient, PromptTemplate promptTemplate, String outputKey,
			OutputParser outputParser) {
		super(aiClient, promptTemplate, outputKey, outputParser);
		this.setMemory(new ConversationBufferMemory());
	}

	public void setInputKey(String inputKey) {
		this.inputKey = inputKey;
	}

	@Override
	public List<String> getInputKeys() {
		return List.of(inputKey);
	}

}
