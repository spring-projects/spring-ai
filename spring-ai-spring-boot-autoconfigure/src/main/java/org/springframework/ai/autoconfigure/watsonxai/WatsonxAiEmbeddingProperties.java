package org.springframework.ai.autoconfigure.watsonxai;

import org.springframework.ai.watsonx.WatsonxAiEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Watsonx.ai Embedding autoconfiguration properties.
 *
 * @author Pablo Sanchidrian Herrera
 * @since 1.0.0
 */
@ConfigurationProperties(WatsonxAiEmbeddingProperties.CONFIG_PREFIX)
public class WatsonxAiEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.watsonx.ai.embedding";

	/**
	 * Enable Watsonx.ai embedding model.
	 */
	private boolean enabled = true;

	/**
	 * Client lever Watsonx.ai embedding options. Use this property to configure the
	 * model. The null values are ignored defaulting to the defaults.
	 */
	@NestedConfigurationProperty
	private WatsonxAiEmbeddingOptions options = WatsonxAiEmbeddingOptions.create()
		.withModel(WatsonxAiEmbeddingOptions.DEFAULT_MODEL);

	public String getModel() {
		return this.options.getModel();
	}

	public void setModel(String model) {
		this.options.setModel(model);
	}

	public WatsonxAiEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

}
