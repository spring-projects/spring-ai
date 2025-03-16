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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

/**
 * Advisor that implements common Retrieval Augmented Generation (RAG) flows using the
 * building blocks defined in the {@link org.springframework.ai.rag} package and following
 * the Modular RAG Architecture.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href="http://export.arxiv.org/abs/2407.21059">arXiv:2407.21059</a>
 * @see <a href="https://export.arxiv.org/abs/2312.10997">arXiv:2312.10997</a>
 * @see <a href="https://export.arxiv.org/abs/2410.20878">arXiv:2410.20878</a>
 */
public final class RetrievalAugmentationAdvisor implements BaseAdvisor {

	public static final String DOCUMENT_CONTEXT = "rag_document_context";

	private final List<QueryTransformer> queryTransformers;

	@Nullable
	private final QueryExpander queryExpander;

	private final DocumentRetriever documentRetriever;

	private final DocumentJoiner documentJoiner;

	private final QueryAugmenter queryAugmenter;

	private final TaskExecutor taskExecutor;

	private final Scheduler scheduler;

	private final int order;

	public RetrievalAugmentationAdvisor(@Nullable List<QueryTransformer> queryTransformers,
			@Nullable QueryExpander queryExpander, DocumentRetriever documentRetriever,
			@Nullable DocumentJoiner documentJoiner, @Nullable QueryAugmenter queryAugmenter,
			@Nullable TaskExecutor taskExecutor, @Nullable Scheduler scheduler, @Nullable Integer order) {
		Assert.notNull(documentRetriever, "documentRetriever cannot be null");
		Assert.noNullElements(queryTransformers, "queryTransformers cannot contain null elements");
		this.queryTransformers = queryTransformers != null ? queryTransformers : List.of();
		this.queryExpander = queryExpander;
		this.documentRetriever = documentRetriever;
		this.documentJoiner = documentJoiner != null ? documentJoiner : new ConcatenationDocumentJoiner();
		this.queryAugmenter = queryAugmenter != null ? queryAugmenter : ContextualQueryAugmenter.builder().build();
		this.taskExecutor = taskExecutor != null ? taskExecutor : buildDefaultTaskExecutor();
		this.scheduler = scheduler != null ? scheduler : BaseAdvisor.DEFAULT_SCHEDULER;
		this.order = order != null ? order : 0;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public AdvisedRequest before(AdvisedRequest request) {
		Map<String, Object> context = new HashMap<>(request.adviseContext());

		// 0. Create a query from the user text, parameters, and conversation history.
		Query originalQuery = Query.builder()
			.text(new PromptTemplate(request.userText(), request.userParams()).render())
			.history(request.messages())
			.context(context)
			.build();

		// 1. Transform original user query based on a chain of query transformers.
		Query transformedQuery = originalQuery;
		for (var queryTransformer : this.queryTransformers) {
			transformedQuery = queryTransformer.apply(transformedQuery);
		}

		// 2. Expand query into one or multiple queries.
		List<Query> expandedQueries = this.queryExpander != null ? this.queryExpander.expand(transformedQuery)
				: List.of(transformedQuery);

		// 3. Get similar documents for each query.
		Map<Query, List<List<Document>>> documentsForQuery = expandedQueries.stream()
			.map(query -> CompletableFuture.supplyAsync(() -> getDocumentsForQuery(query), this.taskExecutor))
			.toList()
			.stream()
			.map(CompletableFuture::join)
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> List.of(entry.getValue())));

		// 4. Combine documents retrieved based on multiple queries and from multiple data
		// sources.
		List<Document> documents = this.documentJoiner.join(documentsForQuery);
		context.put(DOCUMENT_CONTEXT, documents);

		// 5. Augment user query with the document contextual data.
		Query augmentedQuery = this.queryAugmenter.augment(originalQuery, documents);

		// 6. Update advised request with augmented prompt.
		return AdvisedRequest.from(request).userText(augmentedQuery.text()).adviseContext(context).build();
	}

	/**
	 * Processes a single query by routing it to document retrievers and collecting
	 * documents.
	 */
	private Map.Entry<Query, List<Document>> getDocumentsForQuery(Query query) {
		List<Document> documents = this.documentRetriever.retrieve(query);
		return Map.entry(query, documents);
	}

	@Override
	public AdvisedResponse after(AdvisedResponse advisedResponse) {
		ChatResponse.Builder chatResponseBuilder;
		if (advisedResponse.response() == null) {
			chatResponseBuilder = ChatResponse.builder();
		}
		else {
			chatResponseBuilder = ChatResponse.builder().from(advisedResponse.response());
		}
		chatResponseBuilder.metadata(DOCUMENT_CONTEXT, advisedResponse.adviseContext().get(DOCUMENT_CONTEXT));
		return new AdvisedResponse(chatResponseBuilder.build(), advisedResponse.adviseContext());
	}

	@Override
	public Scheduler getScheduler() {
		return this.scheduler;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	private static TaskExecutor buildDefaultTaskExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setThreadNamePrefix("ai-advisor-");
		taskExecutor.setCorePoolSize(4);
		taskExecutor.setMaxPoolSize(16);
		taskExecutor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		taskExecutor.initialize();
		return taskExecutor;
	}

	public static final class Builder {

		private List<QueryTransformer> queryTransformers;

		private QueryExpander queryExpander;

		private DocumentRetriever documentRetriever;

		private DocumentJoiner documentJoiner;

		private QueryAugmenter queryAugmenter;

		private TaskExecutor taskExecutor;

		private Scheduler scheduler;

		private Integer order;

		private Builder() {
		}

		public Builder queryTransformers(List<QueryTransformer> queryTransformers) {
			this.queryTransformers = queryTransformers;
			return this;
		}

		public Builder queryTransformers(QueryTransformer... queryTransformers) {
			this.queryTransformers = Arrays.asList(queryTransformers);
			return this;
		}

		public Builder queryExpander(QueryExpander queryExpander) {
			this.queryExpander = queryExpander;
			return this;
		}

		public Builder documentRetriever(DocumentRetriever documentRetriever) {
			this.documentRetriever = documentRetriever;
			return this;
		}

		public Builder documentJoiner(DocumentJoiner documentJoiner) {
			this.documentJoiner = documentJoiner;
			return this;
		}

		public Builder queryAugmenter(QueryAugmenter queryAugmenter) {
			this.queryAugmenter = queryAugmenter;
			return this;
		}

		public Builder taskExecutor(TaskExecutor taskExecutor) {
			this.taskExecutor = taskExecutor;
			return this;
		}

		public Builder scheduler(Scheduler scheduler) {
			this.scheduler = scheduler;
			return this;
		}

		public Builder order(Integer order) {
			this.order = order;
			return this;
		}

		public RetrievalAugmentationAdvisor build() {
			return new RetrievalAugmentationAdvisor(this.queryTransformers, this.queryExpander, this.documentRetriever,
					this.documentJoiner, this.queryAugmenter, this.taskExecutor, this.scheduler, this.order);
		}

	}

}
