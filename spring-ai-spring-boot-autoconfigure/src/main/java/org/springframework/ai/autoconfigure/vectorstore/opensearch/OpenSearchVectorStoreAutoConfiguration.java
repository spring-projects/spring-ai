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
package org.springframework.ai.autoconfigure.vectorstore.opensearch;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.OpenSearchVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.URISyntaxException;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass({OpenSearchVectorStore.class, EmbeddingClient.class, OpenSearchClient.class})
@EnableConfigurationProperties(OpenSearchVectorStoreProperties.class)
class OpenSearchVectorStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OpenSearchVectorStore vectorStore(OpenSearchVectorStoreProperties properties, OpenSearchClient openSearchClient,
            EmbeddingClient embeddingClient) {
        return new OpenSearchVectorStore(
                Optional.ofNullable(properties.getIndexName()).orElse(OpenSearchVectorStore.DEFAULT_INDEX_NAME),
                openSearchClient, embeddingClient, Optional.ofNullable(properties.getMappingJson())
                .orElse(OpenSearchVectorStore.DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION_1536));
    }

    @Bean
    @ConditionalOnMissingBean
    OpenSearchClient openSearchClient(OpenSearchVectorStoreProperties properties) {
        return new OpenSearchClient(ApacheHttpClient5TransportBuilder.builder(
                properties.getUris().stream().map(s -> creatHttpHost(s)).toArray(HttpHost[]::new)).build());
    }

    private HttpHost creatHttpHost(String s) {
        try {
            return HttpHost.create(s);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
