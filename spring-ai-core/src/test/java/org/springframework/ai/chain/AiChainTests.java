package org.springframework.ai.chain;

import org.junit.jupiter.api.Test;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.parser.OutputParser;
import org.springframework.ai.parser.StringOutputParser;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.ai.prompt.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

public class AiChainTests {

	@Test
	public void renderPromptAndGenerate() {
		AiChain aiChain = setupAiChain();
		AiInput aiInput = new AiInput(Map.of("subject", "cows"));
		AiOutput aiOutput = aiChain.apply(aiInput);
		System.err.println(" --> " + aiOutput.getOutputData());
		assertThat(aiOutput.getOutputData().get("outdata"))
			.isEqualTo("Why did the cow cross the road? To get to the udder side.");
	}

	@Test
	public void renderPromptAndGenerateString() {
		AiChain aiChain = setupAiChain();
		String generation = aiChain.apply(Map.of("subject", "cows"));
		assertThat(generation).isEqualTo("Why did the cow cross the road? To get to the udder side.");
	}

	private static AiChain setupAiChain() {
		AiClient aiClient = mock(AiClient.class);
		Prompt prompt = new Prompt(new UserMessage("Tell me a joke about cows"));
		AiResponse response = new AiResponse(
				List.of(new Generation("Why did the cow cross the road? To get to the udder side.")));
		when(aiClient.generate(prompt)).thenReturn(response);
		PromptTemplate promptTemplate = new PromptTemplate("Tell me a joke about {subject}");
		OutputParser outputParser = new StringOutputParser();

		return new AiChain(aiClient, promptTemplate, "outdata", outputParser);
	}

}
