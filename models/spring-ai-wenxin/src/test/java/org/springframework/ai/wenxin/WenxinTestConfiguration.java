package org.springframework.ai.wenxin;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import static org.springframework.ai.wenxin.api.WenxinApi.EmbeddingModel.TAO_8K;

@SpringBootConfiguration
public class WenxinTestConfiguration {

	@Bean
	public WenxinApi wenxinApi() {
		return new WenxinApi(getAccessKey(), getSecretKey());
	}

	private String getSecretKey() {
		String secretKey = System.getenv("WENXIN_SECRET_KEY");
		if (!StringUtils.hasText(secretKey)) {
			throw new IllegalArgumentException(
					"You must provide an Secret Key.  Put it in an environment variable under the name "
							+ "WENXIN_SECRET_KEY");
		}
		return secretKey;
	}

	private String getAccessKey() {
		String accessKey = System.getenv("WENXIN_ACCESS_KEY");
		if (!StringUtils.hasText(accessKey)) {
			throw new IllegalArgumentException(
					"You must provide an Access Key.  Put it in an environment variable under the name "
							+ "WENXIN_ACCESS_KEY");
		}
		return accessKey;

	}

	@Bean
	public WenxinChatModel wenxinChatModel(WenxinApi api) {
		WenxinChatModel wenxinChatModel = new WenxinChatModel(api);
		return wenxinChatModel;
	}

	@Bean
	WenxinEmbeddingModel wenxinEmbeddingModel(WenxinApi api) {
		WenxinEmbeddingModel wenxinEmbeddingModel = new WenxinEmbeddingModel(api, MetadataMode.EMBED,
				WenxinEmbeddingOptions.builder().withModel(TAO_8K.value).build());
		return wenxinEmbeddingModel;
	}

}
