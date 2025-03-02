package org.springframework.ai.autoconfigure.elevenlabs;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.elevenlabs.ElevenLabsTextToSpeechModel;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link AutoConfiguration Auto-configuration} for ElevenLabs.
 *
 * @author Alexandros Pappas
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
		WebClientAutoConfiguration.class })
@ConditionalOnClass(ElevenLabsApi.class)
@EnableConfigurationProperties({ ElevenLabsSpeechProperties.class, ElevenLabsConnectionProperties.class })
@ConditionalOnProperty(prefix = ElevenLabsSpeechProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class })
public class ElevenLabsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ElevenLabsApi elevenLabsApi(ElevenLabsConnectionProperties connectionProperties,
			ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ResponseErrorHandler responseErrorHandler) {

		return ElevenLabsApi.builder()
			.baseUrl(connectionProperties.getBaseUrl())
			.apiKey(connectionProperties.getApiKey())
			.restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
			.webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
			.responseErrorHandler(responseErrorHandler)
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public ElevenLabsTextToSpeechModel elevenLabsSpeechModel(ElevenLabsApi elevenLabsApi,
			ElevenLabsSpeechProperties speechProperties, RetryTemplate retryTemplate) {

		return ElevenLabsTextToSpeechModel.builder()
			.elevenLabsApi(elevenLabsApi)
			.defaultOptions(speechProperties.getOptions())
			.retryTemplate(retryTemplate)
			.build();
	}

}
