package org.springframework.ai.openai;

import com.theokanning.openai.service.OpenAiService;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.ai.openai.llm.OpenAiClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Properties;

@SpringBootConfiguration
public class OpenAiTestConfiguration {

	@Bean
	public OpenAiService theoOpenAiService() throws IOException {
		// get api token in file ~/.openai
		String apiKey = System.getenv("OPENAI_API_KEY");

		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name OPENAI_API_KEY");
		}
		return new OpenAiService(apiKey);
	}

	@Bean
	public OpenAiClient openAiClient(OpenAiService theoOpenAiService) {
		OpenAiClient openAiClient = new OpenAiClient(theoOpenAiService);
		return openAiClient;
	}

	@Bean
	public OpenAiEmbeddingClient openAiEmbeddingClient(OpenAiService theoOpenAiService) {
		return new OpenAiEmbeddingClient(theoOpenAiService);
	}

}
