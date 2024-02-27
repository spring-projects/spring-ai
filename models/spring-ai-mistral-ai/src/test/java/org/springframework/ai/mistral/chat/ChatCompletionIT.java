package org.springframework.ai.mistral.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistral.MistralAiChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@SpringBootTest
public class ChatCompletionIT {
    @Autowired
    MistralAiChatClient chatClient;

    @Test
    void chatCompletionTest() {
        var message = "Tell me about DC comics.";
        var userMessage = new UserMessage(message);
        var prompt = new Prompt(List.of(userMessage));

        var response = chatClient.call(prompt);

        assertThat(response.getResult().getOutput().getContent()).contains("American");
    }

}
