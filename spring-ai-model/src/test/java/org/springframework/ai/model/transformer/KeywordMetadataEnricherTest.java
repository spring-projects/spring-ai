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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.ai.model.transformer.KeywordMetadataEnricher.*;

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
				new Document("content3"));// @formatter:on
		int keywordCount = 3;

		// 2. Mock
		given(chatModel.call(any(Prompt.class))).willReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword1-1, keyword1-2, keyword1-3")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword2-1, keyword2-2, keyword2-3")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword3-1, keyword3-2, keyword3-3")))));

		// 3. Create instance
		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(chatModel, keywordCount);

		// 4. Apply
		keywordMetadataEnricher.apply(documents);

		// 5. Assert
		verify(chatModel, times(3)).call(promptCaptor.capture());

		assertThat(promptCaptor.getAllValues().get(0).getUserMessage().getText())
			.isEqualTo(getDefaultTemplatePromptText(keywordCount, "content1"));
		assertThat(promptCaptor.getAllValues().get(1).getUserMessage().getText())
			.isEqualTo(getDefaultTemplatePromptText(keywordCount, "content2"));
		assertThat(promptCaptor.getAllValues().get(2).getUserMessage().getText())
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
				new Document("content3"));// @formatter:on
		PromptTemplate promptTemplate = new PromptTemplate(CUSTOM_TEMPLATE);

		// 2. Mock
		given(chatModel.call(any(Prompt.class))).willReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword1-1, keyword1-2, keyword1-3")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword2-1, keyword2-2, keyword2-3")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("keyword3-1, keyword3-2, keyword3-3")))));

		// 3. Create instance
		KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(this.chatModel, promptTemplate);

		// 4. Apply
		keywordMetadataEnricher.apply(documents);

		// 5. Assert
		verify(chatModel, times(documents.size())).call(promptCaptor.capture());

		assertThat(promptCaptor.getAllValues().get(0).getUserMessage().getText())
			.isEqualTo("Custom template: content1");
		assertThat(promptCaptor.getAllValues().get(1).getUserMessage().getText())
			.isEqualTo("Custom template: content2");
		assertThat(promptCaptor.getAllValues().get(2).getUserMessage().getText())
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

		assertThrows(IllegalArgumentException.class, () -> new KeywordMetadataEnricher(chatModel, 0),
				"keywordCount must be >= 1");

		assertThrows(IllegalArgumentException.class, () -> new KeywordMetadataEnricher(chatModel, null),
				"keywordsTemplate must not be null");
	}

	@Test
	void testBuilderThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> KeywordMetadataEnricher.builder(null),
				"The chatModel must not be null");

		Builder builder = builder(chatModel);
		assertThrows(IllegalArgumentException.class, () -> builder.keywordCount(0), "The keywordCount must be >= 1");

		assertThrows(IllegalArgumentException.class, () -> builder.keywordsTemplate(null),
				"The keywordsTemplate must not be null");
	}

	@Test
	void testBuilderWithKeywordCount() {
		int keywordCount = 3;
		KeywordMetadataEnricher enricher = builder(chatModel).keywordCount(keywordCount).build();

		assertThat(enricher.getKeywordsTemplate().getTemplate())
			.isEqualTo(String.format(KEYWORDS_TEMPLATE, keywordCount));
	}

	@Test
	void testBuilderWithKeywordsTemplate() {
		PromptTemplate template = new PromptTemplate(CUSTOM_TEMPLATE);
		KeywordMetadataEnricher enricher = builder(chatModel).keywordsTemplate(template).build();

		assertThat(enricher).extracting("chatModel", "keywordsTemplate").containsExactly(chatModel, template);
	}

	private String getDefaultTemplatePromptText(int keywordCount, String documentContent) {
		PromptTemplate promptTemplate = new PromptTemplate(String.format(KEYWORDS_TEMPLATE, keywordCount));
		Prompt prompt = promptTemplate.create(Map.of(CONTEXT_STR_PLACEHOLDER, documentContent));
		return prompt.getContents();
	}

}
