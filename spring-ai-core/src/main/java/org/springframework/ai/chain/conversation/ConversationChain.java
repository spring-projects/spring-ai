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

	private final String inputKey;

	/**
	 * Constructs a ConversationChain using the default prompt template.
	 *
	 * The default prompt has an input key of "input", so there's no need to specify otherwise in this constructor.
	 *
	 * @param aiClient the `AiClient` to use to generate the response
	 * @param outputKey the key to use for the output data when rendered into an AiOutput
	 * @param outputParser the output parser to use to parse the response
	 */
	public ConversationChain(AiClient aiClient, String outputKey, OutputParser outputParser) {
		this(aiClient, DEFAULT_PROMPT_TEMPLATE, "input", outputKey, outputParser);
	}

	/**
	 * Constructs a ConversationChain with a custom prompt template
	 * @param aiClient the `AiClient` to use to generate the response
	 * @param promptTemplate the `PromptTemplate` to use to render the prompt
	 * @param inputKey the key in the template in which the input will be rendered
	 * @param outputKey the key to use for the output data when rendered into an AiOutput
	 * @param outputParser the output parser to use to parse the response
	 */
	public ConversationChain(AiClient aiClient, PromptTemplate promptTemplate, String inputKey, String outputKey,
			OutputParser outputParser) {
		super(aiClient, promptTemplate, outputKey, outputParser);
		this.inputKey = inputKey;
		this.setMemory(new ConversationBufferMemory());
	}

	@Override
	public List<String> getInputKeys() {
		return List.of(inputKey);
	}

}
