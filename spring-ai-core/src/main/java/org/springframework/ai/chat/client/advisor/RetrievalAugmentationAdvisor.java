/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.chat.client.advisor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.augmentation.ContextualQueryAugmentor;
import org.springframework.ai.rag.augmentation.QueryAugmentor;
import org.springframework.ai.rag.retrieval.source.DocumentRetriever;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * This advisor implements common Retrieval Augmented Generation (RAG) flows using the
 * building blocks defined in the {@link org.springframework.ai.rag} package and following
 * the Modular RAG Architecture.
 * <p>
 * It's the successor of the {@link QuestionAnswerAdvisor}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href="http://export.arxiv.org/abs/2407.21059">arXiv:2407.21059</a>
 * @see <a href="https://export.arxiv.org/abs/2312.10997">arXiv:2312.10997</a>
 */
public class RetrievalAugmentationAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

	public static final String DOCUMENT_CONTEXT = "rag_document_context";

	private final DocumentRetriever documentRetriever;

	private final QueryAugmentor queryAugmentor;

	private final boolean protectFromBlocking;

	private final int order;

	public RetrievalAugmentationAdvisor(DocumentRetriever documentRetriever, @Nullable QueryAugmentor queryAugmentor,
			@Nullable Boolean protectFromBlocking, @Nullable Integer order) {
		Assert.notNull(documentRetriever, "documentRetriever cannot be null");
		this.documentRetriever = documentRetriever;
		this.queryAugmentor = queryAugmentor != null ? queryAugmentor : ContextualQueryAugmentor.builder().build();
		this.protectFromBlocking = protectFromBlocking != null ? protectFromBlocking : false;
		this.order = order != null ? order : 0;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
		Assert.notNull(advisedRequest, "advisedRequest cannot be null");
		Assert.notNull(chain, "chain cannot be null");

		AdvisedRequest processedAdvisedRequest = before(advisedRequest);
		AdvisedResponse advisedResponse = chain.nextAroundCall(processedAdvisedRequest);
		return after(advisedResponse);
	}

	@Override
	public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
		Assert.notNull(advisedRequest, "advisedRequest cannot be null");
		Assert.notNull(chain, "chain cannot be null");

		// This can be executed by both blocking and non-blocking Threads
		// E.g. a command line or Tomcat blocking Thread implementation
		// or by a WebFlux dispatch in a non-blocking manner.
		Flux<AdvisedResponse> advisedResponses = (this.protectFromBlocking) ?
		// @formatter:off
				Mono.just(advisedRequest)
						.publishOn(Schedulers.boundedElastic())
						.map(this::before)
						.flatMapMany(chain::nextAroundStream)
				: chain.nextAroundStream(before(advisedRequest));
		// @formatter:on

		return advisedResponses.map(ar -> {
			if (onFinishReason().test(ar)) {
				ar = after(ar);
			}
			return ar;
		});
	}

	private AdvisedRequest before(AdvisedRequest request) {
		Map<String, Object> context = new HashMap<>(request.adviseContext());

		// 0. Create a query from the user text and parameters.
		Query query = new Query(new PromptTemplate(request.userText(), request.userParams()).render());

		// 1. Retrieve similar documents for the original query.
		List<Document> documents = this.documentRetriever.retrieve(query);
		context.put(DOCUMENT_CONTEXT, documents);

		// 2. Augment user query with the contextual data.
		Query augmentedQuery = this.queryAugmentor.augment(query, documents);

		return AdvisedRequest.from(request).withUserText(augmentedQuery.text()).withAdviseContext(context).build();
	}

	private AdvisedResponse after(AdvisedResponse advisedResponse) {
		ChatResponse.Builder chatResponseBuilder = ChatResponse.builder().from(advisedResponse.response());
		chatResponseBuilder.withMetadata(DOCUMENT_CONTEXT, advisedResponse.adviseContext().get(DOCUMENT_CONTEXT));
		return new AdvisedResponse(chatResponseBuilder.build(), advisedResponse.adviseContext());
	}

	private Predicate<AdvisedResponse> onFinishReason() {
		return advisedResponse -> advisedResponse.response()
			.getResults()
			.stream()
			.anyMatch(result -> result != null && result.getMetadata() != null
					&& StringUtils.hasText(result.getMetadata().getFinishReason()));
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public static final class Builder {

		private DocumentRetriever documentRetriever;

		private QueryAugmentor queryAugmentor;

		private Boolean protectFromBlocking;

		private Integer order;

		private Builder() {
		}

		public Builder documentRetriever(DocumentRetriever documentRetriever) {
			this.documentRetriever = documentRetriever;
			return this;
		}

		public Builder queryAugmentor(QueryAugmentor queryAugmentor) {
			this.queryAugmentor = queryAugmentor;
			return this;
		}

		public Builder protectFromBlocking(Boolean protectFromBlocking) {
			this.protectFromBlocking = protectFromBlocking;
			return this;
		}

		public Builder order(Integer order) {
			this.order = order;
			return this;
		}

		public RetrievalAugmentationAdvisor build() {
			return new RetrievalAugmentationAdvisor(this.documentRetriever, this.queryAugmentor,
					this.protectFromBlocking, this.order);
		}

	}

}
