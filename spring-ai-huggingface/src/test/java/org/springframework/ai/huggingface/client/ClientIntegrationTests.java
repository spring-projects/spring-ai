package org.springframework.ai.huggingface.client;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.huggingface.testutils.AbstractIntegrationTest;
import org.springframework.ai.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ClientIntegrationTests extends AbstractIntegrationTest {

	@Test
	@Disabled
	void helloWorldCompletion() {
		String mistral7bInstruct = """
				[INST] You are a helpful code assistant. Your task is to generate a valid JSON object based on the given information:
				name: John
				lastname: Smith
				address: #1 Samuel St.
				Just generate the JSON object without explanations:
				[/INST]
				 """;
		Prompt prompt = new Prompt(mistral7bInstruct);
		AiResponse aiResponse = huggingfaceAiClient.generate(prompt);
		assertThat(aiResponse.getGeneration().getText()).isNotEmpty();
		String expectedResponse = """
				```json
				{
				    "name": "John",
				    "lastname": "Smith",
				    "address": "#1 Samuel St."
				}
				```""";
		assertThat(aiResponse.getGeneration().getText()).isEqualTo(expectedResponse);
		assertThat(aiResponse.getGeneration().getInfo()).containsKey("generated_tokens");
		assertThat(aiResponse.getGeneration().getInfo()).containsEntry("generated_tokens", 39);

	}

}
