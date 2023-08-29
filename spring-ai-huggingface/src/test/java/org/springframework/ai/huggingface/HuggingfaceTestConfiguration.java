package org.springframework.ai.huggingface;

import org.springframework.ai.huggingface.client.HuggingfaceAiClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class HuggingfaceTestConfiguration {

	@Bean
	public HuggingfaceAiClient huggingfaceAiClient() {
		String apiKey = System.getenv("HUGGINGFACE_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name HUGGINGFACE_API_KEY");
		}
		// Created aws-mistral-7b-instruct-v0-1-805 via
		// https://ui.endpoints.huggingface.co/
		HuggingfaceAiClient huggingfaceAiClient = new HuggingfaceAiClient(apiKey,
				"https://f6hg7b3cvlmntp5i.us-east-1.aws.endpoints.huggingface.cloud");
		return huggingfaceAiClient;
	}

}
