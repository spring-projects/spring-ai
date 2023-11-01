package org.springframework.ai.chain;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.parser.OutputParser;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;

public class AiChain extends AbstractChain {

	private final AiClient aiClient;

	private final PromptTemplate promptTemplate;

	private final String outputKey;

	private final OutputParser outputParser;

	public AiChain(AiClient aiClient, PromptTemplate promptTemplate, String outputKey, OutputParser outputParser) {
		this.aiClient = aiClient;
		this.promptTemplate = promptTemplate;
		this.outputKey = outputKey;
		this.outputParser = outputParser;
	}

	@Override
	public List<String> getInputKeys() {
		return promptTemplate.getInputVariables().stream().toList();
	}

	@Override
	public List<String> getOutputKeys() {
		return List.of(outputKey);
	}

	@Override
	protected AiOutput doApply(AiInput aiInput) {
		Prompt prompt = promptTemplate.create(aiInput.getInputData());
		return new AiOutput(Map.of(outputKey, aiClient.generate(prompt).getGeneration().getText())); // TODO:
																										// Getting
																										// the
																										// text,
																										// not
																										// the
																										// right
																										// thing
	}

}
