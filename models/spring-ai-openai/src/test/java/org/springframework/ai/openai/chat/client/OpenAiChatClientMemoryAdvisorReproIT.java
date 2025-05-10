package org.springframework.ai.openai.chat.client;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@ActiveProfiles("logging-test")
/**
 * Integration test for https://github.com/spring-projects/spring-ai/issues/2339 Verifies
 * that MessageChatMemoryAdvisor works when Prompt is initialized with List<Message>.
 */
class OpenAiChatClientMemoryAdvisorReproIT {

	@Autowired
	private org.springframework.ai.chat.model.ChatModel chatModel;

	@Test
	void messageChatMemoryAdvisor_withPromptMessages_throwsException() {
		// Arrange: create a Prompt with a List<Message> (including UserMessage)
		Message userMessage = new UserMessage("Tell me a joke.");
		List<Message> messages = List.of(userMessage);
		Prompt prompt = new Prompt(messages);
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = new MessageChatMemoryAdvisor(chatMemory);

		ChatClient chatClient = ChatClient.builder(chatModel).defaultAdvisors(advisor).build();

		// Act: call should succeed without exception (issue #2339 is fixed)
		chatClient.prompt(prompt).call().chatResponse(); // Should not throw

	}

}
