package org.springframework.ai.openai;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.client.OpenAiClient;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class OpenAiTestConfiguration {

	@Bean
	public OpenAiApi openAiApi() {
		String apiKey = getApiKey();
		OpenAiApi openAiService = new OpenAiApi(apiKey);
		return openAiService;
	}

	private String getApiKey() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name OPENAI_API_KEY");
		}
		return apiKey;
	}

	@Bean
	public OpenAiClient openAiClient(OpenAiApi api) {
		OpenAiClient openAiClient = new OpenAiClient(api);
		openAiClient.setTemperature(0.3);
		return openAiClient;
	}

	@Bean
	public EmbeddingClient openAiEmbeddingClient(OpenAiApi api) {
		return new OpenAiEmbeddingClient(api);
	}

}
