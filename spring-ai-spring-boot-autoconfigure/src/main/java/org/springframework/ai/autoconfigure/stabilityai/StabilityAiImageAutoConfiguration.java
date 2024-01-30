package org.springframework.ai.autoconfigure.stabilityai;

import org.springframework.ai.autoconfigure.NativeHints;
import org.springframework.ai.stabilityai.StabilityAiImageClient;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;

@AutoConfiguration
@ConditionalOnClass(StabilityAiApi.class)
@EnableConfigurationProperties({ StabilityAiProperties.class })
@ImportRuntimeHints(NativeHints.class)
public class StabilityAiImageAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public StabilityAiApi stabilityAiApi(StabilityAiProperties stabilityAiProperties) {
		return new StabilityAiApi(stabilityAiProperties.getApiKey(), stabilityAiProperties.getBaseUrl(),
				stabilityAiProperties.getOptions().getModel());
	}

	@Bean
	@ConditionalOnMissingBean
	public StabilityAiImageClient stabilityAiImageClient(StabilityAiApi stabilityAiApi,
			StabilityAiProperties stabilityAiProperties) {
		return new StabilityAiImageClient(stabilityAiApi, stabilityAiProperties.getOptions());
	}

}
