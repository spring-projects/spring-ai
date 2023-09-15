package org.springframework.ai.openai;

import com.theokanning.openai.service.OpenAiService;
import org.springframework.ai.annotations.SpringAIFunction;
import org.springframework.ai.openai.client.OpenAiClient;
import org.springframework.ai.openai.client.OpenAiFunctionManager;
import org.springframework.ai.openai.embedding.OpenAiEmbeddingClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.function.Function;

@SpringBootConfiguration
public class OpenAiTestConfiguration {

	@Bean
	public OpenAiService theoOpenAiService() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name OPENAI_API_KEY");
		}
		return new OpenAiService(apiKey, Duration.ofSeconds(60));
	}

	@Bean
	public OpenAiClient openAiClient(OpenAiService theoOpenAiService, OpenAiFunctionManager functionManager) {
		OpenAiClient openAiClient = new OpenAiClient(theoOpenAiService, functionManager);
		openAiClient.setTemperature(0.3);
		return openAiClient;
	}

	@Bean
	public OpenAiFunctionManager functionManager() {
		return new OpenAiFunctionManager();
	}

	@SpringAIFunction(name = "get_weather", description = "Get the current weather of a location",
			classType = Weather.class)
	public Function<Weather, Weather> getWeather() {
		return s -> new Weather("winston", "90f");
	}

	@Bean
	public OpenAiEmbeddingClient openAiEmbeddingClient(OpenAiService theoOpenAiService) {
		return new OpenAiEmbeddingClient(theoOpenAiService);
	}

	public record Weather(String name, String temp) {
	}

}
