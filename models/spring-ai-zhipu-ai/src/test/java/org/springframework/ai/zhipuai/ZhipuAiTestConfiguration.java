package org.springframework.ai.zhipuai;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class ZhipuAiTestConfiguration {

	@Bean
	public ZhipuAiApi zhipuAiApi() {
		var apiKey = System.getenv("ZHIPU_AI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"Missing ZHIPU_AI_API_KEY environment variable. Please set it to your Zhipu AI API key.");
		}
		return new ZhipuAiApi(apiKey);
	}

	@Bean
	public EmbeddingClient embeddingClient(ZhipuAiApi api) {
		return new ZhipuAiEmbeddingClient(api,
				ZhipuAiEmbeddingOptions.builder().withModel(ZhipuAiApi.EmbeddingModel.EMBED.getValue()).build());
	}

	@Bean
	public ZhipuAiChatClient chatClient(ZhipuAiApi zhipuAiApi) {
		return new ZhipuAiChatClient(zhipuAiApi,
				ZhipuAiChatOptions.builder().withModel(ZhipuAiApi.ChatModel.GLM_4.getValue()).build());
	}

}
