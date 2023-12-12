package org.springframework.ai.openai;

import java.time.Duration;

import com.theokanning.openai.service.OpenAiService;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.client.OpenAiClient;
import org.springframework.ai.openai.client.OpenAiStreamClient;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class OpenAiTestConfiguration {

	@Bean
	public OpenAiService theoOpenAiService() {
		String apiKey = getApiKey();
		OpenAiService openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
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
	public OpenAiClient openAiClient(OpenAiService theoOpenAiService) {
		OpenAiClient openAiClient = new OpenAiClient(theoOpenAiService);
		openAiClient.setTemperature(0.3);
		return openAiClient;
	}

	@Bean
	public EmbeddingClient openAiEmbeddingClient(OpenAiService theoOpenAiService) {
		return new OpenAiEmbeddingClient(theoOpenAiService);
	}

	@Bean
	public OpenAiStreamClient openAiStreamClient() {
		return new OpenAiStreamClient(getApiKey());
	}

}
