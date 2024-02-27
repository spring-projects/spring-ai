package org.springframework.ai.mistral.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistral.MistralAiChatClient;
import org.springframework.ai.mistral.MistralAiChatOptions;
import org.springframework.ai.mistral.api.MistralAiApi;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@SpringBootTest
public class ChatCompletionRequestTest {
    MistralAiChatClient chatClient = new MistralAiChatClient(new MistralAiApi("test"));
    @Test
    void chatCompletionDefaultRequestTest() {

        var request = chatClient.createRequest(new Prompt("test content"), false);

        assertThat(request.messages()).hasSize(1);
        assertThat(request.topP()).isEqualTo(1);
        assertThat(request.temperature()).isEqualTo(0.7f);
        assertThat(request.safePrompt()).isFalse();
        assertThat(request.maxTokens()).isNull();
    }

    @Test
    void chatCompletionRequestWithOptionsTest() {

        var options = MistralAiChatOptions.builder()
                .withTemperature(0.5f)
                .withTopP(0.8f)
                .withStream(true)
                .build();

        var request = chatClient.createRequest(new Prompt("test content", options), false);

        assertThat(request.messages().size()).isEqualTo(1);
        assertThat(request.topP()).isEqualTo(0.8f);
        assertThat(request.temperature()).isEqualTo(0.5f);
        assertThat(request.stream()).isTrue();
    }
}
