package org.springframework.ai.autoconfigure.azure.openai;

import com.azure.ai.openai.OpenAIClientBuilder;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link OpenAIClientBuilder} whilst retaining the default auto-configuration.
 */
@FunctionalInterface
public interface OpenAIClientBuilderCustomizer {

	/**
	 * Customize the {@link OpenAIClientBuilder}.
	 * @param clientBuilder the {@link OpenAIClientBuilder} to customize
	 */
	void customize(OpenAIClientBuilder clientBuilder);

}
