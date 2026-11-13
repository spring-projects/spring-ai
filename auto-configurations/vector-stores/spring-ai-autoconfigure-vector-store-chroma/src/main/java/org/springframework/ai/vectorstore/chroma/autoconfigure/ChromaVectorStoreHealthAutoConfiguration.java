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

package org.springframework.ai.vectorstore.chroma.autoconfigure;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for {@link ChromaVectorStore} health
 * indicator.
 *
 * @author Prasad Anandappa
 * @since 2.1.0
 */
@AutoConfiguration(after = ChromaVectorStoreAutoConfiguration.class)
@ConditionalOnClass({ ChromaVectorStore.class, ChromaApi.class, HealthIndicator.class })
@ConditionalOnBean(ChromaVectorStore.class)
@ConditionalOnEnabledHealthIndicator("chroma")
public class ChromaVectorStoreHealthAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "chromaHealthIndicator")
	public HealthIndicator chromaHealthIndicator(ChromaApi chromaApi) {
		return new ChromaVectorStoreHealthIndicator(chromaApi);
	}

}
