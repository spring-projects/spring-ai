/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.transformer;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.embedding.EmbeddingModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * DocumentEmbeddingTransformer injects embedding values into each Document using the
 * EmbeddingModel if the Document does not already have embedding data.
 *
 * @author youngmoneee
 * @since 1.0.0
 */
public class DocumentEmbeddingTransformer implements DocumentTransformer {

	private final EmbeddingModel embeddingModel;

	public DocumentEmbeddingTransformer(EmbeddingModel embeddingModel) {
		this.embeddingModel = embeddingModel;
	}

	/**
	 * Embedding values are generated using the embedding model provided through the
	 * constructor and then injected into each Document object.
	 * @param documents to process.
	 * @return processed documents
	 */
	@Override
	public List<Document> apply(List<Document> documents) {
		return Flux.fromIterable(documents).flatMap(document -> {
			if (document.getEmbedding() == null || document.getEmbedding().length == 0)
				return Mono
					.zip(Mono.just(document), Mono.fromCallable(() -> embeddingModel.embed(document)), (doc, embed) -> {
						doc.setEmbedding(embed);
						return doc;
					})
					.subscribeOn(Schedulers.boundedElastic());
			return Mono.just(document);
		}).collectList().block();
	}

}
