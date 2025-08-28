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

package org.springframework.ai.rag.preretrieval.query.transformation;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link RewriteQueryTransformer}.
 *
 * @author Thomas Vitale
 */
class RewriteQueryTransformerTests {

	@Test
	void whenChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> RewriteQueryTransformer.builder().chatClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");
	}

	@Test
	void whenQueryIsNullThenThrow() {
		QueryTransformer queryTransformer = RewriteQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.build();
		assertThatThrownBy(() -> queryTransformer.transform(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null");
	}

	@Test
	void whenPromptHasMissingTargetPlaceholderThenThrow() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Rewrite {query}");
		assertThatThrownBy(() -> RewriteQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.targetSearchSystem("vector store")
			.promptTemplate(customPromptTemplate)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The following placeholders must be present in the prompt template")
			.hasMessageContaining("target");
	}

	@Test
	void whenPromptHasMissingQueryPlaceholderThenThrow() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Rewrite for {target}");
		assertThatThrownBy(() -> RewriteQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.targetSearchSystem("search engine")
			.promptTemplate(customPromptTemplate)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The following placeholders must be present in the prompt template")
			.hasMessageContaining("query");
	}

	@Test
	void shouldLoggingWithMissingTargetPlaceholderInWarnMode() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Rewrite {query}");
		Logger logger = (Logger) LoggerFactory.getLogger(RewriteQueryTransformer.class);

		ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
		listAppender.start();
		logger.addAppender(listAppender);

		RewriteQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.targetSearchSystem("vector store")
			.validationMode(ValidationMode.WARN)
			.promptTemplate(customPromptTemplate)
			.build();
		var logsList = listAppender.list;

		assertThat(logsList).isNotEmpty();
		assertThat(logsList.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
		assertThat(logsList.get(0).getMessage())
			.contains("The following placeholders must be present in the prompt template: target");
	}

	@Test
	void shouldLoggingWithMissingQueryPlaceholderInWarnMode() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Rewrite for {target}");
		Logger logger = (Logger) LoggerFactory.getLogger(RewriteQueryTransformer.class);

		ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
		listAppender.start();
		logger.addAppender(listAppender);

		RewriteQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.targetSearchSystem("search engine")
			.validationMode(ValidationMode.WARN)
			.promptTemplate(customPromptTemplate)
			.build();
		var logsList = listAppender.list;

		assertThat(logsList).isNotEmpty();
		assertThat(logsList.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
		assertThat(logsList.get(0).getMessage())
			.contains("The following placeholders must be present in the prompt template: query");
	}

	@Test
	void shouldContinueWithMissingTargetPlaceholderInNoneMode() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Rewrite {target}");
		Logger logger = (Logger) LoggerFactory.getLogger(RewriteQueryTransformer.class);

		ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
		listAppender.start();
		logger.addAppender(listAppender);

		RewriteQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.targetSearchSystem("vector store")
			.validationMode(ValidationMode.NONE)
			.promptTemplate(customPromptTemplate)
			.build();
		var logsList = listAppender.list;

		assertThat(logsList).isEmpty();
	}

	@Test
	void shouldContinueWithMissingQueryPlaceholderInNoneMode() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Rewrite {query}");
		Logger logger = (Logger) LoggerFactory.getLogger(RewriteQueryTransformer.class);

		ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
		listAppender.start();
		logger.addAppender(listAppender);

		RewriteQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.targetSearchSystem("search engine")
			.validationMode(ValidationMode.NONE)
			.promptTemplate(customPromptTemplate)
			.build();
		var logsList = listAppender.list;

		assertThat(logsList).isEmpty();
	}

}
