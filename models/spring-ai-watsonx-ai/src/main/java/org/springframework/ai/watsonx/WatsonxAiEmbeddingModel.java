package org.springframework.ai.watsonx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.watsonx.api.WatsonxAiApi;
import org.springframework.ai.watsonx.api.WatsonxAiEmbeddingRequest;
import org.springframework.ai.watsonx.api.WatsonxAiEmbeddingResponse;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link EmbeddingModel} implementation for {@literal Watsonx.ai}.
 * <p>
 * Watsonx.ai allows developers to run large language models and generate embeddings. It
 * supports open-source models available on <a href=
 * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx">Watsonx.ai
 * models</a>.
 * <p>
 * Please refer to the <a href="https://www.ibm.com/products/watsonx-ai/">official
 * Watsonx.ai website</a> for the most up-to-date information on available models.
 *
 * @author Pablo Sanchidrian Herrera
 * @since 1.0.0
 */
public class WatsonxAiEmbeddingModel extends AbstractEmbeddingModel {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final WatsonxAiApi watsonxAiApi;

	/**
	 * Default options to be used for all embedding requests.
	 */
	private WatsonxAiEmbeddingOptions defaultOptions = WatsonxAiEmbeddingOptions.create()
		.withModel(WatsonxAiEmbeddingOptions.DEFAULT_MODEL);

	public WatsonxAiEmbeddingModel(WatsonxAiApi watsonxAiApi) {
		this.watsonxAiApi = watsonxAiApi;
	}

	public WatsonxAiEmbeddingModel(WatsonxAiApi watsonxAiApi, WatsonxAiEmbeddingOptions defaultOptions) {
		this.watsonxAiApi = watsonxAiApi;
		this.defaultOptions = defaultOptions;
	}

	@Override
	public float[] embed(Document document) {
		return embed(document.getContent());
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		Assert.notEmpty(request.getInstructions(), "At least one text is required!");

		WatsonxAiEmbeddingRequest embeddingRequest = watsonxAiEmbeddingRequest(request.getInstructions(),
				request.getOptions());
		WatsonxAiEmbeddingResponse response = this.watsonxAiApi.embeddings(embeddingRequest).getBody();

		AtomicInteger indexCounter = new AtomicInteger(0);
		List<Embedding> embeddings = response.results()
			.stream()
			.map(e -> new Embedding(e.embedding(), indexCounter.getAndIncrement()))
			.toList();

		return new EmbeddingResponse(embeddings);
	}

	WatsonxAiEmbeddingRequest watsonxAiEmbeddingRequest(List<String> inputs, EmbeddingOptions options) {

		WatsonxAiEmbeddingOptions runtimeOptions = (options instanceof WatsonxAiEmbeddingOptions)
				? (WatsonxAiEmbeddingOptions) options : this.defaultOptions;

		if (!StringUtils.hasText(runtimeOptions.getModel())) {
			this.logger.warn("The model cannot be null, using default model instead");
			runtimeOptions = this.defaultOptions;
		}

		return WatsonxAiEmbeddingRequest.builder(inputs).withModel(runtimeOptions.getModel()).build();
	}

}
