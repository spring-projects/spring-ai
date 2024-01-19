package org.springframework.ai.stabilityai;

import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class StabilityAiImageTestConfiguration {

	@Bean
	public StabilityAiApi stabilityAiApi() {
		return new StabilityAiApi(getApiKey());
	}

	@Bean
	StabilityAiImageClient stabilityAiImageClient(StabilityAiApi stabilityAiApi) {
		return new StabilityAiImageClient(stabilityAiApi);
	}

	private String getApiKey() {
		String apiKey = System.getenv("STABILITYAI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name STABILITYAI_API_KEY");
		}
		return apiKey;
	}

}
