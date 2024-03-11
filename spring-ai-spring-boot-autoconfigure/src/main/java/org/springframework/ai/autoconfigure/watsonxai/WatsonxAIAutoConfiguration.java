package org.springframework.ai.autoconfigure.watsonxai;

import org.springframework.ai.autoconfigure.ollama.OllamaChatProperties;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.watsonx.WatsonxChatClient;
import org.springframework.ai.watsonx.api.WatsonxAIApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * WatsonX.ai autoconfiguration class.
 *
 * @author Pablo Sanchidrian Herrera
 * @author John Jario Moreno Rojas
 * @since 0.8.1
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(WatsonxAIApi.class)
@EnableConfigurationProperties({ WatsonxAIConnectionProperties.class })
public class WatsonxAIAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public WatsonxAIApi watsonxApi(WatsonxAIConnectionProperties properties, RestClient.Builder restClientBuilder) {
		return new WatsonxAIApi(properties.getBaseUrl(), properties.getStreamEndpoint(), properties.getTextEndpoint(),
				properties.getProjectId(), properties.getIAMToken(), restClientBuilder);
	}

	@Bean
	@ConditionalOnMissingBean
	public WatsonxChatClient watsonxChatClient(WatsonxAIApi watsonxApi) {
		return new WatsonxChatClient(watsonxApi);
	}

}
