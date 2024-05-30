package org.springframework.ai.yi;

import org.springframework.ai.yi.api.YiAiApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class YiAiTestConfiguration {

	@Bean
	public YiAiApi yiAiApi() {
		return new YiAiApi(getApiKey());
	}

	private String getApiKey() {
		String apiKey = System.getenv("YI_AI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name ZHIPU_AI_API_KEY");
		}
		return apiKey;
	}

	@Bean
	public YiAiChatModel yiAiChatModel(YiAiApi api) {
		return new YiAiChatModel(api);
	}

}
