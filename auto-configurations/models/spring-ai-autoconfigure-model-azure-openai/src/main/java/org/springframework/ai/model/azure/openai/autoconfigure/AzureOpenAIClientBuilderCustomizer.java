package org.springframework.ai.model.azure.openai.autoconfigure;

import com.azure.ai.openai.OpenAIClientBuilder;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link OpenAIClientBuilder} whilst retaining the default auto-configuration.
 *
 * @author Manuel Andreo Garcia
 * @since 1.0.0-M6
 */
@FunctionalInterface
public interface AzureOpenAIClientBuilderCustomizer {

	/**
	 * Customize the {@link OpenAIClientBuilder}.
	 * @param clientBuilder the {@link OpenAIClientBuilder} to customize
	 */
	void customize(OpenAIClientBuilder clientBuilder);

}
