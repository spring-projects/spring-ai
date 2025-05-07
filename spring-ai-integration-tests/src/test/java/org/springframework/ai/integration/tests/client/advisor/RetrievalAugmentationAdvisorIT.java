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

package org.springframework.ai.integration.tests.client.advisor;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RetrievalAugmentationAdvisor}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class RetrievalAugmentationAdvisorIT {

	private List<Document> knowledgeBaseDocuments;

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Autowired
	PgVectorStore pgVectorStore;

	@Value("${classpath:documents/knowledge-base.md}")
	Resource knowledgeBaseResource;

	@BeforeEach
	void setUp() {
		DocumentReader markdownReader = new MarkdownDocumentReader(this.knowledgeBaseResource,
				MarkdownDocumentReaderConfig.defaultConfig());
		this.knowledgeBaseDocuments = markdownReader.read();
		this.pgVectorStore.add(this.knowledgeBaseDocuments);
	}

	@AfterEach
	void tearDown() {
		this.pgVectorStore.delete(this.knowledgeBaseDocuments.stream().map(Document::getId).toList());
	}

	@Test
	void ragBasic() {
		String question = "Where does the adventure of Anacletus and Birba take place?";

		RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(this.pgVectorStore).build())
			.build();

		ChatResponse chatResponse = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt(question)
			.advisors(ragAdvisor)
			.call()
			.chatResponse();

		assertThat(chatResponse).isNotNull();

		String response = chatResponse.getResult().getOutput().getText();
		System.out.println(response);
		assertThat(response).containsIgnoringCase("Highlands");

		evaluateRelevancy(question, chatResponse);
	}

	@Test
	void ragWithRequestFilter() {
		String question = "Where does the adventure of Anacletus and Birba take place?";

		RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(this.pgVectorStore).build())
			.build();

		ChatResponse chatResponse = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt(question)
			.advisors(ragAdvisor)
			.advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, "location == 'Italy'"))
			.call()
			.chatResponse();

		assertThat(chatResponse).isNotNull();
		// No documents retrieved since the filter expression matches none of the
		// documents in the vector store.
		assertThat((String) chatResponse.getResult().getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT))
			.isNull();
	}

	@Test
	void ragWithCompression() {
		MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor
			.builder(MessageWindowChatMemory.builder().build())
			.build();

		RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
			.queryTransformers(CompressionQueryTransformer.builder()
				.chatClientBuilder(ChatClient.builder(this.openAiChatModel))
				.build())
			.documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(this.pgVectorStore).build())
			.build();

		ChatClient chatClient = ChatClient.builder(this.openAiChatModel)
			.defaultAdvisors(memoryAdvisor, ragAdvisor)
			.build();

		String conversationId = "007";

		ChatResponse chatResponse1 = chatClient.prompt()
			.user("Where does the adventure of Anacletus and Birba take place?")
			.advisors(advisors -> advisors.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY,
					conversationId))
			.call()
			.chatResponse();

		assertThat(chatResponse1).isNotNull();
		String response1 = chatResponse1.getResult().getOutput().getText();
		System.out.println(response1);

		ChatResponse chatResponse2 = chatClient.prompt()
			.user("Did they meet any cow?")
			.advisors(advisors -> advisors.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY,
					conversationId))
			.call()
			.chatResponse();

		assertThat(chatResponse2).isNotNull();
		String response2 = chatResponse2.getResult().getOutput().getText();
		System.out.println(response2);
		assertThat(response2.toLowerCase()).containsIgnoringCase("Fergus");
	}

	@Test
	void ragWithRewrite() {
		String question = "Where are the main characters going?";

		RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
			.queryTransformers(RewriteQueryTransformer.builder()
				.chatClientBuilder(ChatClient.builder(this.openAiChatModel))
				.targetSearchSystem("vector store")
				.build())
			.documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(this.pgVectorStore).build())
			.build();

		ChatResponse chatResponse = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.user(question)
			.advisors(ragAdvisor)
			.call()
			.chatResponse();

		assertThat(chatResponse).isNotNull();

		String response = chatResponse.getResult().getOutput().getText();
		System.out.println(response);
		assertThat(response).containsIgnoringCase("Loch of the Stars");

		evaluateRelevancy(question, chatResponse);
	}

	@Test
	void ragWithTranslation() {
		String question = "Hvor finder Anacletus og Birbas eventyr sted?";

		RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
			.queryTransformers(TranslationQueryTransformer.builder()
				.chatClientBuilder(ChatClient.builder(this.openAiChatModel))
				.targetLanguage("english")
				.build())
			.documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(this.pgVectorStore).build())
			.build();

		ChatResponse chatResponse = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt()
			.system("Answer the question in English")
			.user(question)
			.advisors(ragAdvisor)
			.call()
			.chatResponse();

		assertThat(chatResponse).isNotNull();

		String response = chatResponse.getResult().getOutput().getText();
		System.out.println(response);
		assertThat(response.toLowerCase()).containsAnyOf("highlands", "højland");

		evaluateRelevancy(question, chatResponse);
	}

	@Test
	void ragWithMultiQuery() {
		String question = "Where does the adventure of Anacletus and Birba take place?";

		RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
			.queryExpander(MultiQueryExpander.builder()
				.chatClientBuilder(ChatClient.builder(this.openAiChatModel))
				.numberOfQueries(2)
				.build())
			.documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(this.pgVectorStore).build())
			.build();

		ChatResponse chatResponse = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt(question)
			.advisors(ragAdvisor)
			.call()
			.chatResponse();

		assertThat(chatResponse).isNotNull();

		String response = chatResponse.getResult().getOutput().getText();
		System.out.println(response);
		assertThat(response).containsIgnoringCase("Highlands");

		evaluateRelevancy(question, chatResponse);
	}

	@Test
	void ragWithDocumentPostProcessor() {
		String question = "Where does the adventure of Anacletus and Birba take place?";

		RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(this.pgVectorStore).build())
			.documentPostProcessors((query, documents) -> List
				.of(Document.builder().text("The adventure of Anacletus and Birba takes place in Molise").build()))
			.build();

		ChatResponse chatResponse = ChatClient.builder(this.openAiChatModel)
			.build()
			.prompt(question)
			.advisors(ragAdvisor)
			.call()
			.chatResponse();

		assertThat(chatResponse).isNotNull();

		String response = chatResponse.getResult().getOutput().getText();
		System.out.println(response);
		assertThat(response).containsIgnoringCase("Molise");

		evaluateRelevancy(question, chatResponse);
	}

	private void evaluateRelevancy(String question, ChatResponse chatResponse) {
		EvaluationRequest evaluationRequest = new EvaluationRequest(question,
				chatResponse.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT),
				chatResponse.getResult().getOutput().getText());
		RelevancyEvaluator evaluator = new RelevancyEvaluator(ChatClient.builder(this.openAiChatModel));
		EvaluationResponse evaluationResponse = evaluator.evaluate(evaluationRequest);
		assertThat(evaluationResponse.isPass()).isTrue();
	}

}
