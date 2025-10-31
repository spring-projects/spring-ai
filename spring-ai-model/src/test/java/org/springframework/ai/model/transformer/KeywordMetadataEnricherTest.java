/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.transformer;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.ai.model.transformer.KeywordMetadataEnricher.Builder;
import static org.springframework.ai.model.transformer.KeywordMetadataEnricher.CONTEXT_STR_PLACEHOLDER;
import static org.springframework.ai.model.transformer.KeywordMetadataEnricher.EXCERPT_KEYWORDS_METADATA_KEY;
import static org.springframework.ai.model.transformer.KeywordMetadataEnricher.KEYWORDS_TEMPLATE;
import static org.springframework.ai.model.transformer.KeywordMetadataEnricher.builder;

/**
 * @author YunKui Lu
 */
@ExtendWith(MockitoExtension.class)
class KeywordMetadataEnricherTest {

	@Mock
	private ChatModel chatModel;

	@Captor
	private ArgumentCaptor<Prompt> promptCaptor;

	private final String CUSTOM_TEMPLATE = "Custom template: {context_str}";

	@Test
	void testUseWithDefaultTemplate() {
		// 1. Prepare test data
		// @formatter:off
		List<Document> documents = List.of(
				new Document("content1"),
				new Document("content2"),
				new Document("content3")); // @formatter:on
		int keywordCount = 3;

		// 2. Mock
		given(this.chatModel.call(any(Prompt.class))).willReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword1-1, keyword1-2, keyword1-3")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword2-1, keyword2-2, keyword2-3")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword3-1, keyword3-2, keyword3-3")))));

		// 3. Create instance
		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, keywordCount);

		// 4. Apply
		keywordMetadataEnricher.apply(documents);

		// 5. Assert
		verify(this.chatModel, times(3)).call(this.promptCaptor.capture());

		assertThat(this.promptCaptor.getAllValues().get(0).getUserMessage().getText())
			.isEqualTo(getDefaultTemplatePromptText(keywordCount, "content1"));
		assertThat(this.promptCaptor.getAllValues().get(1).getUserMessage().getText())
			.isEqualTo(getDefaultTemplatePromptText(keywordCount, "content2"));
		assertThat(this.promptCaptor.getAllValues().get(2).getUserMessage().getText())
			.isEqualTo(getDefaultTemplatePromptText(keywordCount, "content3"));

		assertThat(documents.get(0).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY,
				"keyword1-1, keyword1-2, keyword1-3");
		assertThat(documents.get(1).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY,
				"keyword2-1, keyword2-2, keyword2-3");
		assertThat(documents.get(2).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY,
				"keyword3-1, keyword3-2, keyword3-3");
	}

	@Test
	void testUseCustomTemplate() {
		// 1. Prepare test data
		// @formatter:off
		List<Document> documents = List.of(
				new Document("content1"),
				new Document("content2"),
				new Document("content3")); // @formatter:on
		PromptTemplate promptTemplate = new PromptTemplate(this.CUSTOM_TEMPLATE);

		// 2. Mock
		given(this.chatModel.call(any(Prompt.class))).willReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword1-1, keyword1-2, keyword1-3")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword2-1, keyword2-2, keyword2-3")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword3-1, keyword3-2, keyword3-3")))));

		// 3. Create instance
		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, promptTemplate);

		// 4. Apply
		keywordMetadataEnricher.apply(documents);

		// 5. Assert
		verify(this.chatModel, times(documents.size())).call(this.promptCaptor.capture());

		assertThat(this.promptCaptor.getAllValues().get(0).getUserMessage().getText())
			.isEqualTo("Custom template: content1");
		assertThat(this.promptCaptor.getAllValues().get(1).getUserMessage().getText())
			.isEqualTo("Custom template: content2");
		assertThat(this.promptCaptor.getAllValues().get(2).getUserMessage().getText())
			.isEqualTo("Custom template: content3");

		assertThat(documents.get(0).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY,
				"keyword1-1, keyword1-2, keyword1-3");
		assertThat(documents.get(1).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY,
				"keyword2-1, keyword2-2, keyword2-3");
		assertThat(documents.get(2).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY,
				"keyword3-1, keyword3-2, keyword3-3");
	}

	@Test
	void testConstructorThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> new KeywordMetadataEnricher(null, 3),
				"chatModel must not be null");

		assertThrows(IllegalArgumentException.class, () -> new KeywordMetadataEnricher(this.chatModel, 0),
				"keywordCount must be >= 1");

		assertThrows(IllegalArgumentException.class, () -> new KeywordMetadataEnricher(this.chatModel, null),
				"keywordsTemplate must not be null");
	}

	@Test
	void testBuilderThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> builder(null), "The chatModel must not be null");

		Builder builder = builder(this.chatModel);
		assertThrows(IllegalArgumentException.class, () -> builder.keywordCount(0), "The keywordCount must be >= 1");

		assertThrows(IllegalArgumentException.class, () -> builder.keywordsTemplate(null),
				"The keywordsTemplate must not be null");
	}

	@Test
	void testBuilderWithKeywordCount() {
		int keywordCount = 3;
		KeywordMetadataEnricher enricher = builder(this.chatModel).keywordCount(keywordCount).build();

		assertThat(enricher.getKeywordsTemplate().getTemplate())
			.isEqualTo(String.format(KEYWORDS_TEMPLATE, keywordCount));
	}

	@Test
	void testBuilderWithKeywordsTemplate() {
		PromptTemplate template = new PromptTemplate(this.CUSTOM_TEMPLATE);
		KeywordMetadataEnricher enricher = builder(this.chatModel).keywordsTemplate(template).build();

		assertThat(enricher).extracting("chatModel", "keywordsTemplate").containsExactly(this.chatModel, template);
	}

	private String getDefaultTemplatePromptText(int keywordCount, String documentContent) {
		PromptTemplate promptTemplate = new PromptTemplate(String.format(KEYWORDS_TEMPLATE, keywordCount));
		Prompt prompt = promptTemplate.create(Map.of(CONTEXT_STR_PLACEHOLDER, documentContent));
		return prompt.getContents();
	}

	@Test
	void testApplyWithEmptyDocumentsList() {
		List<Document> emptyDocuments = List.of();
		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, 3);

		keywordMetadataEnricher.apply(emptyDocuments);

		verify(this.chatModel, never()).call(any(Prompt.class));
	}

	@Test
	void testApplyWithSingleDocument() {
		List<Document> documents = List.of(new Document("single content"));
		given(this.chatModel.call(any(Prompt.class))).willReturn(new ChatResponse(
				List.of(new Generation(new AssistantMessage("single, keyword, test, document, content")))));

		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, 5);
		keywordMetadataEnricher.apply(documents);

		verify(this.chatModel, times(1)).call(this.promptCaptor.capture());
		assertThat(documents.get(0).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY,
				"single, keyword, test, document, content");
	}

	@Test
	void testApplyWithDocumentContainingExistingMetadata() {
		Document document = new Document("content with existing metadata");
		document.getMetadata().put("existing_key", "existing_value");
		List<Document> documents = List.of(document);
		given(this.chatModel.call(any(Prompt.class)))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("new, keywords")))));

		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, 2);
		keywordMetadataEnricher.apply(documents);

		assertThat(documents.get(0).getMetadata()).containsEntry("existing_key", "existing_value");
		assertThat(documents.get(0).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY, "new, keywords");
	}

	@Test
	void testApplyWithEmptyStringResponse() {
		List<Document> documents = List.of(new Document("content"));
		given(this.chatModel.call(any(Prompt.class)))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("")))));

		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, 3);
		keywordMetadataEnricher.apply(documents);

		assertThat(documents.get(0).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY, "");
	}

	@Test
	void testApplyWithWhitespaceOnlyResponse() {
		List<Document> documents = List.of(new Document("content"));
		given(this.chatModel.call(any(Prompt.class)))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("   \n\t   ")))));

		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, 3);
		keywordMetadataEnricher.apply(documents);

		assertThat(documents.get(0).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY, "   \n\t   ");
	}

	@Test
	void testApplyOverwritesExistingKeywords() {
		Document document = new Document("content");
		document.getMetadata().put(EXCERPT_KEYWORDS_METADATA_KEY, "old, keywords");
		List<Document> documents = List.of(document);
		given(this.chatModel.call(any(Prompt.class)))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("new, keywords")))));

		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, 2);
		keywordMetadataEnricher.apply(documents);

		assertThat(documents.get(0).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY, "new, keywords");
	}

	@Test
	void testBuilderWithBothKeywordCountAndTemplate() {
		PromptTemplate customTemplate = new PromptTemplate(this.CUSTOM_TEMPLATE);

		KeywordMetadataEnricher enricher = builder(this.chatModel).keywordCount(5)
			.keywordsTemplate(customTemplate)
			.build();

		assertThat(enricher.getKeywordsTemplate()).isEqualTo(customTemplate);
	}

	@Test
	void testApplyWithSpecialCharactersInContent() {
		List<Document> documents = List.of(new Document("Content with special chars: @#$%^&*()"));
		given(this.chatModel.call(any(Prompt.class))).willReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("special, characters, content")))));

		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, 3);
		keywordMetadataEnricher.apply(documents);

		verify(this.chatModel, times(1)).call(this.promptCaptor.capture());
		assertThat(this.promptCaptor.getValue().getUserMessage().getText())
			.contains("Content with special chars: @#$%^&*()");
		assertThat(documents.get(0).getMetadata()).containsEntry(EXCERPT_KEYWORDS_METADATA_KEY,
				"special, characters, content");
	}

}
