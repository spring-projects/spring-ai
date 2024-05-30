package org.springframework.ai.autoconfigure.wenxin;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.wenxin.WenxinEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午5:56
 * @description:
 */
@ConfigurationProperties(WenxinEmbeddingProperties.CONFIG_PREFIX)
public class WenxinEmbeddingProperties extends WenxinParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.wenxin.embedding";

	public static final String DEFAULT_EMBEDDING_MODEL = "Embedding-V1";

	private boolean enabled = true;

	private MetadataMode metadataMode = MetadataMode.EMBED;

	private WenxinEmbeddingOptions options = WenxinEmbeddingOptions.builder()
		.withModel(DEFAULT_EMBEDDING_MODEL)
		.build();

	public WenxinEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(WenxinEmbeddingOptions options) {
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
