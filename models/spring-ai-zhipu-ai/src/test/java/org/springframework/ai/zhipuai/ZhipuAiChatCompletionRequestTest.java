package org.springframework.ai.zhipuai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".+")
public class ZhipuAiChatCompletionRequestTest {

	ZhipuAiChatClient chatClient = new ZhipuAiChatClient(new ZhipuAiApi("test"));

	@Test
	void chatCompletionDefaultRequestTest() {

		var request = chatClient.createRequest(new Prompt("test content"), false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.topP()).isEqualTo(0.7f);
		assertThat(request.temperature()).isEqualTo(0.95f);
		assertThat(request.maxTokens()).isNull();
		assertThat(request.stream()).isFalse();
	}

	@Test
	void chatCompletionRequestWithOptionsTest() {

		var options = ZhipuAiChatOptions.builder().withTemperature(0.5f).withTopP(0.8f).build();

		var request = chatClient.createRequest(new Prompt("test content", options), true);

		assertThat(request.messages().size()).isEqualTo(1);
		assertThat(request.topP()).isEqualTo(0.8f);
		assertThat(request.temperature()).isEqualTo(0.5f);
		assertThat(request.stream()).isTrue();
	}

}
