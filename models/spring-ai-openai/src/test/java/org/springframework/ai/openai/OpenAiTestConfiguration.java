package org.springframework.ai.openai;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class OpenAiTestConfiguration {

	@Bean
	public OpenAiApi openAiApi() {
		return new OpenAiApi(getApiKey());
	}

	@Bean
	public OpenAiImageApi openAiImageApi() {
		return new OpenAiImageApi(getApiKey());
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
	public OpenAiChatClient openAiChatClient(OpenAiApi api) {
		OpenAiChatClient openAiChatClient = new OpenAiChatClient(api);
		openAiChatClient.setTemperature(0.3);
		return openAiChatClient;
	}

	@Bean
	public OpenAiImageClient openAiImageClient(OpenAiImageApi imageApi) {
		OpenAiImageClient openAiImageClient = new OpenAiImageClient(imageApi);
		// openAiImageClient.setModel("foobar");
		return openAiImageClient;
	}

	@Bean
	public EmbeddingClient openAiEmbeddingClient(OpenAiApi api) {
		return new OpenAiEmbeddingClient(api);
	}

}
