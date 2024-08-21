package org.springframework.ai.wenxin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.ai.wenxin.metadata.WenxinUsage;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.util.List;

/**
 * @author lvchzh
 * @since 1.0.0
 */
public class WenxinEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(WenxinEmbeddingModel.class);

	private final WenxinEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final WenxinApi wenxinApi;

	private final MetadataMode metadataMode;

	public WenxinEmbeddingModel(WenxinApi wenxinApi) {
		this(wenxinApi, MetadataMode.EMBED);
	}

	public WenxinEmbeddingModel(WenxinApi wenxinApi, MetadataMode metadataMode) {
		this(wenxinApi, metadataMode,
				WenxinEmbeddingOptions.builder().withModel(WenxinApi.DEFAULT_EMBEDDING_MODEL).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public WenxinEmbeddingModel(WenxinApi wenxinApi, MetadataMode metadataMode,
			WenxinEmbeddingOptions wenxinEmbeddingOptions) {
		this(wenxinApi, metadataMode, wenxinEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public WenxinEmbeddingModel(WenxinApi wenxinApi, MetadataMode metadataMode, WenxinEmbeddingOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(wenxinApi, "WenxinApi must not be null");
		Assert.notNull(metadataMode, "MetadataMode must not be null");
		Assert.notNull(options, "WenxinEmbeddingOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");

		this.wenxinApi = wenxinApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@SuppressWarnings("unchecked")
	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {

		return this.retryTemplate.execute(ctx -> {

			WenxinApi.EmbeddingRequest<List<String>> apiRequest = (this.defaultOptions != null)
					? new WenxinApi.EmbeddingRequest<>(request.getInstructions(), this.defaultOptions.getModel(),
							this.defaultOptions.getUserId())
					: new WenxinApi.EmbeddingRequest<>(request.getInstructions(), WenxinApi.DEFAULT_EMBEDDING_MODEL);

			if (request.getOptions() != null) {
				apiRequest = ModelOptionsUtils.merge(request.getOptions(), apiRequest,
						WenxinApi.EmbeddingRequest.class);
			}

			WenxinApi.EmbeddingList<WenxinApi.Embedding> apiEmbeddingResponse = this.wenxinApi.embeddings(apiRequest)
				.getBody();

			if (apiEmbeddingResponse == null) {
				logger.warn("No embeddings returned from request: {}", request);
				return new EmbeddingResponse(List.of());
			}

			// var metadata = generateResponseMetadata(apiEmbeddingResponse.id(),
			// apiEmbeddingResponse.object(),
			// apiEmbeddingResponse.created(), apiEmbeddingResponse.usage());

			var metadata = new EmbeddingResponseMetadata(apiRequest.model(),
					WenxinUsage.from(apiEmbeddingResponse.usage()));

			List<Embedding> embeddings = apiEmbeddingResponse.data()
				.stream()
				.map(e -> new Embedding(e.embedding(), e.index()))
				.toList();

			return new EmbeddingResponse(embeddings, metadata);
		});
	}

}
