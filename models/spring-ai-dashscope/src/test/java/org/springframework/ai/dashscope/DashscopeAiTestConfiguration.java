package org.springframework.ai.dashscope;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.dashscope.api.DashscopeApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author nottyjay
 */
@SpringBootConfiguration
public class DashscopeAiTestConfiguration {

	@Bean
	public DashscopeApi dashscopeApi() {
		return DashscopeApi.builder().withApiKey(getApiKey()).build();
	}

	@Bean
	public EmbeddingModel dashscopeEmbeddingModel(DashscopeApi dashscopeApi) {
		return new DashscopeEmbeddingModel(dashscopeApi);
	}

	@Bean
	public ChatModel dashscopeChatModel(DashscopeApi dashscopeApi) {
		return new DashscopeChatModel(dashscopeApi);
	}

	private String getApiKey() {
		String apiKey = System.getenv("DASHSCOPE_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name DASHSCOPE_API_KEY");
		}
		return apiKey;
	}

}
