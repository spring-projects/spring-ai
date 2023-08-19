package org.springframework.ai.openai.client;

import org.junit.jupiter.api.Test;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.openai.testutils.AbstractIntegrationTest;
import org.springframework.ai.parser.JsonOutputParser;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.ai.prompt.SystemPromptTemplate;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClientIntegrationTests extends AbstractIntegrationTest {

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void roleTest() {
		String request = "Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.";
		String name = "Bob";
		String voice = "pirate";
		UserMessage userMessage = new UserMessage(request);
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", name, "voice", voice));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		AiResponse response = openAiClient.generate(prompt);
		evaluateQuestionAndAnswer(request, response, false);
	}

	@Test
	void outputParser() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputParser outputParser = new ListOutputParser(conversionService);

		String format = outputParser.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = openAiClient.generate(prompt).getGeneration();

		List<String> list = outputParser.parse(generation.getText());
		System.out.println(list);
		assertThat(list).hasSize(5);

	}

	@Test
	void jsonOutputParser() {
		JsonOutputParser outputParser = new JsonOutputParser(HashMap.class);

		String format = outputParser.getFormat();
		String template = """
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = openAiClient.generate(prompt).getGeneration();

		Object result = outputParser.parse(generation.getText());
		System.out.println(result);
		assertThat(result).isNotNull();
		assertThat(((Map) result).get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

}
