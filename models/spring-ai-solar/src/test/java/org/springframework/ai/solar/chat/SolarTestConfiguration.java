package org.springframework.ai.solar.chat;

import org.springframework.ai.solar.api.SolarApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author Seunghyeon Ji
 */
@SpringBootConfiguration
public class SolarTestConfiguration {

	@Bean
	public SolarApi solarApi() {
		return new SolarApi(getApiKey());
	}

	private String getApiKey() {
		String apiKey = System.getenv("SOLAR_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key. Put it in an environment variable under the name SOLAR_API_KEY");
		}
		return apiKey;
	}

	@Bean
	public SolarChatModel solarChatModel(SolarApi api) {
		return new SolarChatModel(api);
	}

}
