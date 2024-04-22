package org.springframework.ai.autoconfigure.zhipuai;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.zhipuai.ZhipuAiEmbeddingOptions;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Ricken Bazolo
 */
@ConfigurationProperties(ZhipuAiEmbeddingProperties.CONFIG_PREFIX)
public class ZhipuAiEmbeddingProperties extends ZhipuAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.zhipuai.embedding";

	public static final String DEFAULT_EMBEDDING_MODEL = ZhipuAiApi.EmbeddingModel.EMBED.getValue();

	/**
	 * Enable MistralAI embedding client.
	 */
	private boolean enabled = true;

	public MetadataMode metadataMode = MetadataMode.EMBED;

	@NestedConfigurationProperty
	private ZhipuAiEmbeddingOptions options = ZhipuAiEmbeddingOptions.builder()
		.withModel(DEFAULT_EMBEDDING_MODEL)
		.build();

	public ZhipuAiEmbeddingProperties() {
		super.setBaseUrl(ZhipuAiCommonProperties.DEFAULT_BASE_URL);
	}

	public ZhipuAiEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(ZhipuAiEmbeddingOptions options) {
		this.options = options;
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
