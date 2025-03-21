package org.springframework.ai.integration.tests.client.advisor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MessageChatMemoryAdvisor}.
 *
 * @author Alexandros Pappas
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class MessageChatMemoryAdvisorIT {

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Test
	void chatMemoryStoresAndRecallsConversation() {
		var chatMemory = new InMemoryChatMemory();
		var conversationId = "test-conversation";

		var memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).conversationId(conversationId).build();

		var chatClient = ChatClient.builder(openAiChatModel).defaultAdvisors(memoryAdvisor).build();

		// First interaction
		ChatResponse response1 = chatClient.prompt().user("Hello, my name is John.").call().chatResponse();

		assertThat(response1).isNotNull();
		String assistantReply1 = response1.getResult().getOutput().getText();
		System.out.println("Assistant reply 1: " + assistantReply1);

		// Second interaction - Verify memory recall
		ChatResponse response2 = chatClient.prompt().user("What is my name?").call().chatResponse();

		assertThat(response2).isNotNull();
		String assistantReply2 = response2.getResult().getOutput().getText();
		System.out.println("Assistant reply 2: " + assistantReply2);

		assertThat(assistantReply2.toLowerCase()).contains("john");
	}

	@Test
	void separateConversationsDoNotMixMemory() {
		var chatMemory = new InMemoryChatMemory();

		var memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

		var chatClient = ChatClient.builder(openAiChatModel).defaultAdvisors(memoryAdvisor).build();

		// First conversation
		chatClient.prompt()
			.user("Remember my secret code is blue.")
			.advisors(advisors -> advisors.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, "conv-1"))
			.call();

		// Second conversation
		ChatResponse response = chatClient.prompt()
			.user("Do you remember my secret code?")
			.advisors(advisors -> advisors.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, "conv-2"))
			.call()
			.chatResponse();

		assertThat(response).isNotNull();
		String assistantReply = response.getResult().getOutput().getText();
		System.out.println("Assistant reply: " + assistantReply);

		assertThat(assistantReply.toLowerCase()).doesNotContain("blue");
	}

}
