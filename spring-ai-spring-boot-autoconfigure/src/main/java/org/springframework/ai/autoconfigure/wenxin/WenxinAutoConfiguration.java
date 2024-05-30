package org.springframework.ai.autoconfigure.wenxin;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.wenxin.WenxinChatModel;
import org.springframework.ai.wenxin.WenxinEmbeddingModel;
import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午5:54
 * @description:
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(WenxinApi.class)
@EnableConfigurationProperties({ WenxinConnectionProperties.class, WenxinChatProperties.class,
		WenxinEmbeddingProperties.class })
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class })
public class WenxinAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = WenxinChatProperties.CONFIG_PREFIX, name = "enable", havingValue = "true",
			matchIfMissing = true)
	public WenxinChatModel wenxinChatModel(WenxinConnectionProperties commonProperties,
			WenxinChatProperties chatProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, List<FunctionCallback> toolFunctionCallbacks,
			FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {
		var wenxinApi = wenxinApi(chatProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				chatProperties.getAccessKey(), commonProperties.getAccessKey(), chatProperties.getSecretKey(),
				commonProperties.getSecretKey(), restClientBuilder, webClientBuilder, responseErrorHandler);

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		return new WenxinChatModel(wenxinApi, chatProperties.getOptions(), functionCallbackContext, retryTemplate);

	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = WenxinEmbeddingProperties.CONFIG_PREFIX, name = "enable", havingValue = "true",
			matchIfMissing = true)
	public WenxinEmbeddingModel wenxinEmbeddingModel(WenxinConnectionProperties commonProperties,
			WenxinEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {
		var wenxinApi = wenxinApi(embeddingProperties.getBaseUrl(), commonProperties.getBaseUrl(),
				embeddingProperties.getAccessKey(), commonProperties.getAccessKey(), embeddingProperties.getSecretKey(),
				commonProperties.getSecretKey(), restClientBuilder, webClientBuilder, responseErrorHandler);

		return new WenxinEmbeddingModel(wenxinApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate);
	}

	private WenxinApi wenxinApi(String chatBaseUrl, String commonBaseUrl, String accessKey, String commonAccessKey,
			String secretKey, String commonSecretKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		String resolvedChatBaseUrl = StringUtils.hasText(chatBaseUrl) ? chatBaseUrl : commonBaseUrl;
		Assert.hasText(resolvedChatBaseUrl, "The Wenxin API base URL must be set!");

		String resolvedAccessKey = StringUtils.hasText(secretKey) ? secretKey : commonAccessKey;
		Assert.hasText(resolvedAccessKey, "The Wenxin API client ID must be set!");

		String resolvedSecretKey = StringUtils.hasText(accessKey) ? accessKey : commonSecretKey;
		Assert.hasText(resolvedSecretKey, "The Wenxin API client secret must be set!");

		return new WenxinApi(resolvedChatBaseUrl, restClientBuilder, webClientBuilder, responseErrorHandler,
				resolvedAccessKey, resolvedSecretKey);
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionCallbackContext(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

}
