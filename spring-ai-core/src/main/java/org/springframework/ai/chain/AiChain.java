package org.springframework.ai.chain;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.parser.OutputParser;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * Simple chain that renders a prompt from a prompt template and generates a response.
 *
 * @author Craig Walls
 */
public class AiChain extends AbstractChain {

	private final AiClient aiClient;

	private final PromptTemplate promptTemplate;

	private final String outputKey;

	private final OutputParser outputParser;

	/**
	 * Constructs an AiChain.
	 *
	 * @param aiClient The AI client to use to generate the response
	 * @param promptTemplate The prompt template to use to render the prompt
	 * @param outputKey The key to use for the output data when rendered into an AiOutput
	 * @param outputParser The output parser to use to parse the response
	 */
	public AiChain(AiClient aiClient, PromptTemplate promptTemplate, String outputKey, OutputParser outputParser) {
		this.aiClient = aiClient;
		this.promptTemplate = promptTemplate;
		this.outputKey = outputKey;
		this.outputParser = outputParser;
	}

	/**
	 * Returns the input keys that the prompt template requires.
	 * @return the input keys that the prompt template requires.
	 */
	@Override
	public List<String> getInputKeys() {
		return promptTemplate.getInputVariables().stream().toList();
	}

	/**
	 * Returns the output keys that the output parser will produce.
	 * @return the output keys that the output parser will produce.
	 */
	@Override
	public List<String> getOutputKeys() {
		return List.of(outputKey);
	}

	/**
	 * Renders a prompt from the prompt template and generates a response.
	 * @param aiInput The input data to use to render the prompt
	 * @return an AiOutput containing the generated response
	 */
	@Override
	protected AiOutput doApply(AiInput aiInput) {
		Prompt prompt = promptTemplate.create(aiInput.getInputData());
		String generationText = aiClient.generate(prompt).getGeneration().getText();
		return new AiOutput(Map.of(outputKey, outputParser.parse(generationText)));
	}

}
