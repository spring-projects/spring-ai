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

package org.springframework.ai.vectorstore.mongodb.autoconfigure;

import com.mongodb.MongoDriverInformation;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.mongodb.atlas.MongoDBAtlasVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MongoDBAtlasVectorStoreAutoConfiguration}.
 *
 * @author Almas Abdrazak
 * @author Soby Chacko
 */
class MongoDBAtlasVectorStoreAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class, DataMongoAutoConfiguration.class,
				MongoDBAtlasVectorStoreAutoConfiguration.class))
		.withPropertyValues("spring.ai.vectorstore.mongodb.initialize-schema=false")
		.withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class));

	@Test
	void appendsFrameworkMetadataWhenMongoClientBeanIsPresent() {
		MongoClient mongoClient = mock(MongoClient.class);
		this.contextRunner.withBean(MongoClient.class, () -> mongoClient).run(context -> {
			assertThat(context).hasSingleBean(MongoDBAtlasVectorStore.class);

			ArgumentCaptor<MongoDriverInformation> captor = ArgumentCaptor.forClass(MongoDriverInformation.class);
			verify(mongoClient, times(1)).appendMetadata(captor.capture());
			assertThat(captor.getValue().getDriverNames()).contains("spring-ai");
		});
	}

}
