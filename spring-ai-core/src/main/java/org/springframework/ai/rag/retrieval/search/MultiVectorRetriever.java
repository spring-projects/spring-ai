/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.ai.rag.retrieval.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentStore;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Retrieves from a set of multiple embeddings for the same document.
 * <p>
 * Example usage:
 * <pre>{@code
 * MultiVectorRetriever retriever = MultiVectorRetriever.builder()
 *     .vectorStore(vectorStore)
 *     .docStore(docStore)
 *     .similarityThreshold(0.75)
 *     .topK(5)
 *     .filterExpression(filterExpression)
 *     .build();
 * List<Document> documents = retriever.retrieve(new Query("example query"));
 * }</pre>
 *
 * @author Seunggyu Lee
 * @since 1.0.0
 */
public class MultiVectorRetriever implements DocumentRetriever {

    private final VectorStore vectorStore;
    private final DocumentStore docStore;
    private final Double similarityThreshold;
    private final Integer topK;
    private final Supplier<Filter.Expression> filterExpression;
    private final String parentIdKey;

    private MultiVectorRetriever(VectorStore vectorStore, DocumentStore docStore,
                                 @Nullable Double similarityThreshold, @Nullable Integer topK,
                                 @Nullable Supplier<Filter.Expression> filterExpression, String parentIdKey) {
        Assert.notNull(vectorStore, "vectorStore cannot be null");
        Assert.notNull(docStore, "docStore cannot be null");
        Assert.isTrue(similarityThreshold == null || similarityThreshold >= 0.0,
                      "similarityThreshold must be >= 0.0");
        Assert.isTrue(topK == null || topK > 0, "topK must be > 0");
        Assert.hasText(parentIdKey, "parentIdKey must not be empty");
        this.vectorStore = vectorStore;
        this.docStore = docStore;
        this.similarityThreshold = (similarityThreshold != null)
                ? similarityThreshold
                : SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL;
        this.topK = (topK != null) ? topK : SearchRequest.DEFAULT_TOP_K;
        this.filterExpression = (filterExpression != null) ? filterExpression : () -> null;
        this.parentIdKey = parentIdKey;
    }

    @Override
    public List<Document> retrieve(Query query) {
        Assert.notNull(query, "query cannot be null");
        SearchRequest searchRequest = SearchRequest.builder()
                                                   .query(query.text())
                                                   .filterExpression(this.filterExpression.get())
                                                   .similarityThreshold(this.similarityThreshold)
                                                   .topK(this.topK)
                                                   .build();

        List<Document> subDocs = this.vectorStore.similaritySearch(searchRequest);
        if (subDocs == null || subDocs.isEmpty()) {
            return subDocs == null ? new ArrayList<>() : subDocs;
        }

        List<String> parentIds = new ArrayList<>();
        for (Document chunk : subDocs) {
            String pid = (String) chunk.getMetadata().get(this.parentIdKey);
            if (pid != null && !parentIds.contains(pid)) {
                parentIds.add(pid);
            }
        }
        List<Document> parentDocs = this.docStore.get(parentIds);
        parentDocs.removeIf(Objects::isNull);
        return parentDocs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private VectorStore vectorStore;
        private DocumentStore docStore;
        private Double similarityThreshold;
        private Integer topK;
        private Supplier<Filter.Expression> filterExpression;
        private String parentIdKey = "doc_id";

        public Builder vectorStore(VectorStore vectorStore) {
            this.vectorStore = vectorStore;
            return this;
        }

        public Builder docStore(DocumentStore docStore) {
            this.docStore = docStore;
            return this;
        }

        public Builder similarityThreshold(Double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder filterExpression(Filter.Expression filterExpression) {
            this.filterExpression = () -> filterExpression;
            return this;
        }

        public Builder filterExpression(Supplier<Filter.Expression> filterExpression) {
            this.filterExpression = filterExpression;
            return this;
        }

        public Builder parentIdKey(String parentIdKey) {
            this.parentIdKey = parentIdKey;
            return this;
        }

        public MultiVectorRetriever build() {
            return new MultiVectorRetriever(this.vectorStore, this.docStore,
                                            this.similarityThreshold, this.topK, this.filterExpression, this.parentIdKey);
        }
    }
}
