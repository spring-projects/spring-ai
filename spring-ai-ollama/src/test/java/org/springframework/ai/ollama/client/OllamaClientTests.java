package org.springframework.ai.ollama.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.prompt.Prompt;
import org.springframework.util.CollectionUtils;

import java.util.function.Consumer;

public class OllamaClientTests {

	@Test
	@Disabled("For manual smoke testing only.")
	public void smokeTest() {
		OllamaClient ollama2 = getOllamaClient();

		Prompt prompt = new Prompt("Hello");
		AiResponse aiResponse = ollama2.generate(prompt);
		Assertions.assertNotNull(aiResponse);
		Assertions.assertFalse(CollectionUtils.isEmpty(aiResponse.getGenerations()));
		Assertions.assertNotNull(aiResponse.getGeneration());
		Assertions.assertNotNull(aiResponse.getGeneration().getText());
	}

	private static OllamaClient getOllamaClient() {
		Consumer<OllamaGenerateResult> ollamaGenerateResultConsumer = it -> {
			if (it.getDone()) {
				System.out.println();
				System.out.printf("Total duration: %dms%n", it.getTotalDuration() / 1000 / 1000);
				System.out.printf("Prompt tokens: %d%n", it.getPromptEvalCount());
				System.out.printf("Completion tokens: %d%n", it.getEvalCount());
			}
			else {
				System.out.print(it.getResponse());
			}
		};

		return new OllamaClient("http://127.0.0.1:11434", "llama2", ollamaGenerateResultConsumer);
	}

}
