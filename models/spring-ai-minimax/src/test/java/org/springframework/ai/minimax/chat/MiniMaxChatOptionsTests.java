package org.springframework.ai.minimax.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
@EnabledIfEnvironmentVariable(named = "MINIMAX_API_KEY", matches = ".+")
public class MiniMaxChatOptionsTests {

	private final MiniMaxChatModel chatModel = new MiniMaxChatModel(new MiniMaxApi(System.getenv("MINIMAX_API_KEY")));

	@Test
	void testMarkSensitiveInfo() {

		UserMessage userMessage = new UserMessage(
				"Please extract the phone number, the content: My name is Bob, and my phone number is 133-12345678");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		// markSensitiveInfo is enabled by default
		ChatResponse response = chatModel.call(new Prompt(messages));
		String responseContent = response.getResult().getOutput().getContent();

		assertThat(responseContent).contains("133-**");
		assertThat(responseContent).doesNotContain("133-12345678");

		var chatOptions = MiniMaxChatOptions.builder().withMaskSensitiveInfo(false).build();

		ChatResponse unmaskResponse = chatModel.call(new Prompt(messages, chatOptions));
		String unmaskResponseContent = unmaskResponse.getResult().getOutput().getContent();

		assertThat(unmaskResponseContent).contains("133-12345678");
	}

}
