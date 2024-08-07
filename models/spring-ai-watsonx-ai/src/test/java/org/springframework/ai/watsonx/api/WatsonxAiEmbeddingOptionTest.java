package org.springframework.ai.watsonx.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.springframework.ai.watsonx.WatsonxAiEmbeddingOptions;

/**
 * @author Pablo Sanchidrian Herrera
 */
public class WatsonxAiEmbeddingOptionTest {

	@Test
	public void testWithModel() {
		WatsonxAiEmbeddingOptions options = new WatsonxAiEmbeddingOptions();
		options.withModel("test-model");
		assertThat("test-model").isEqualTo(options.getModel());
	}

	@Test
	public void testCreateFactoryMethod() {
		WatsonxAiEmbeddingOptions options = WatsonxAiEmbeddingOptions.create();
		assertThat(options).isNotNull();
		assertThat(options.getModel()).isNull();
	}

	@Test
	public void testFromOptionsFactoryMethod() {
		WatsonxAiEmbeddingOptions originalOptions = new WatsonxAiEmbeddingOptions().withModel("original-model");
		WatsonxAiEmbeddingOptions newOptions = WatsonxAiEmbeddingOptions.fromOptions(originalOptions);

		assertThat(newOptions).isNotNull();
		assertThat("original-model").isEqualTo(newOptions.getModel());
	}

}
