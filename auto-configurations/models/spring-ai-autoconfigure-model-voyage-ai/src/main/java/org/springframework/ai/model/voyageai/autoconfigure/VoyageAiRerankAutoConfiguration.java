/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.voyageai.autoconfigure;

import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.VoyageAiDocumentReranker;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Rerank {@link AutoConfiguration Auto-configuration} for Voyage AI. It is active when
 * {@code spring.ai.voyageai.rerank.model} is set and the RAG module is on the classpath.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
@AutoConfiguration
@EnableConfigurationProperties({ VoyageAiConnectionProperties.class, VoyageAiRerankProperties.class })
@ConditionalOnClass({ VoyageAiApi.class, VoyageAiDocumentReranker.class, DocumentPostProcessor.class })
@ConditionalOnProperty(prefix = VoyageAiRerankProperties.CONFIG_PREFIX, name = "model")
public class VoyageAiRerankAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VoyageAiDocumentReranker voyageAiDocumentReranker(VoyageAiConnectionProperties connectionProperties,
			VoyageAiRerankProperties rerankProperties, ObjectProvider<RestClient.Builder> restClientBuilder,
			ObjectProvider<RetryTemplate> retryTemplate, ObjectProvider<ResponseErrorHandler> responseErrorHandler) {

		var builder = VoyageAiDocumentReranker.builder()
			.voyageAiApi(VoyageAiApiFactory.create(connectionProperties, restClientBuilder, responseErrorHandler))
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE));
		String model = rerankProperties.getModel();
		if (model != null) {
			builder.model(model);
		}
		if (rerankProperties.getTopK() != null) {
			builder.topK(rerankProperties.getTopK());
		}
		if (rerankProperties.getTruncation() != null) {
			builder.truncation(rerankProperties.getTruncation());
		}
		return builder.build();
	}

}
