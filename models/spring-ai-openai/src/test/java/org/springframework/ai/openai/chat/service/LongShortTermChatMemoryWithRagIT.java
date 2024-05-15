/*
 * Copyright 2024 - 2024 the original author or authors.
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

package org.springframework.ai.openai.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import org.springframework.ai.chat.service.ChatService;
import org.springframework.ai.chat.service.PromptTransformingChatService;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryChatServiceListener;
import org.springframework.ai.chat.memory.ChatMemoryRetriever;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.memory.LastMaxTokenSizeContentTransformer;
import org.springframework.ai.chat.memory.SystemPromptChatMemoryAugmentor;
import org.springframework.ai.chat.memory.VectorStoreChatMemoryChatServiceListener;
import org.springframework.ai.chat.memory.VectorStoreChatMemoryRetriever;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.transformer.QuestionContextAugmentor;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;
import org.springframework.ai.chat.prompt.transformer.VectorStoreRetriever;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_TURBO_PREVIEW;

@Testcontainers
@SpringBootTest(classes = LongShortTermChatMemoryWithRagIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class LongShortTermChatMemoryWithRagIT {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String COLLECTION_NAME = "test_collection";

	private static final int QDRANT_GRPC_PORT = 6334;

	@Container
	static QdrantContainer qdrantContainer = new QdrantContainer("qdrant/qdrant:v1.9.2");

	@Autowired
	ChatService chatService;

	@Autowired
	RelevancyEvaluator relevancyEvaluator;

	@Autowired
	VectorStore vectorStore;

	@Value("classpath:/data/acme/bikes.json")
	private Resource bikesResource;

	void loadData() {

		var metadataEnricher = new DocumentTransformer() {

			@Override
			public List<Document> apply(List<Document> documents) {
				documents.forEach(d -> {
					Map<String, Object> metadata = d.getMetadata();
					metadata.put(TransformerContentType.EXTERNAL_KNOWLEDGE, "true");
				});

				return documents;
			}

		};

		JsonReader jsonReader = new JsonReader(bikesResource, "name", "price", "shortDescription", "description");
		var textSplitter = new TokenTextSplitter();
		vectorStore.accept(metadataEnricher.apply(textSplitter.apply(jsonReader.get())));
	}

	// @Autowired
	// StreamingChatService streamingChatService;

	@Test
	void memoryChatService() {

		loadData();

		var prompt = new Prompt(new UserMessage("My name is Christian and I like mountain bikes."));
		ChatServiceContext chatServiceContext = new ChatServiceContext(prompt);

		var chatServiceResponse1 = this.chatService.call(chatServiceContext);

		logger.info("Response1: " + chatServiceResponse1.getChatResponse().getResult().getOutput().getContent());

		var chatServiceResponse2 = this.chatService.call(new ChatServiceContext(
				new Prompt(new String("What is my name and what bike model would you suggest for me?"))));
		logger.info("Response2: " + chatServiceResponse2.getChatResponse().getResult().getOutput().getContent());

		// logger.info(chatServiceResponse2.getPromptContext().getContents().toString());
		assertThat(chatServiceResponse2.getChatResponse().getResult().getOutput().getContent()).contains("Christian");

		EvaluationResponse evaluationResponse = this.relevancyEvaluator
			.evaluate(new EvaluationRequest(chatServiceResponse2));

		assertTrue(evaluationResponse.isPass(), "Response is not relevant to the question");

	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public ChatMemory chatHistory() {
			return new InMemoryChatMemory();
		}

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public OpenAiChatClient openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatClient(openAiApi);
		}

		@Bean
		public OpenAiEmbeddingClient embeddingClient(OpenAiApi openAiApi) {
			return new OpenAiEmbeddingClient(openAiApi);
		}

		@Bean
		public VectorStore qdrantVectorStore(EmbeddingClient embeddingClient) {
			QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient
				.newBuilder(qdrantContainer.getHost(), qdrantContainer.getMappedPort(QDRANT_GRPC_PORT), false)
				.build());
			return new QdrantVectorStore(qdrantClient, COLLECTION_NAME, embeddingClient);
		}

		@Bean
		public TokenCountEstimator tokenCountEstimator() {
			return new JTokkitTokenCountEstimator();
		}

		@Bean
		public ChatService memoryChatService(OpenAiChatClient chatClient, VectorStore vectorStore,
				TokenCountEstimator tokenCountEstimator, ChatMemory chatHistory) {

			return PromptTransformingChatService.builder(chatClient)
				.withRetrievers(List.of(new VectorStoreRetriever(vectorStore, SearchRequest.defaults()),
						ChatMemoryRetriever.builder()
							.withChatHistory(chatHistory)
							.withMetadata(Map.of(TransformerContentType.SHORT_TERM_MEMORY, ""))
							.build(),
						new VectorStoreChatMemoryRetriever(vectorStore, 10,
								Map.of(TransformerContentType.LONG_TERM_MEMORY, ""))))

				.withContentPostProcessors(List.of(
						new LastMaxTokenSizeContentTransformer(tokenCountEstimator, 1000,
								Set.of(TransformerContentType.SHORT_TERM_MEMORY)),
						new LastMaxTokenSizeContentTransformer(tokenCountEstimator, 1000,
								Set.of(TransformerContentType.LONG_TERM_MEMORY)),
						new LastMaxTokenSizeContentTransformer(tokenCountEstimator, 2000,
								Set.of(TransformerContentType.EXTERNAL_KNOWLEDGE))))
				.withAugmentors(List.of(new QuestionContextAugmentor(),
						new SystemPromptChatMemoryAugmentor(
								"""
										Use the long term conversation history from the LONG TERM HISTORY section to provide accurate answers.

										LONG TERM HISTORY:
										{history}
											""",
								Set.of(TransformerContentType.LONG_TERM_MEMORY)),
						new SystemPromptChatMemoryAugmentor(Set.of(TransformerContentType.SHORT_TERM_MEMORY))))

				.withChatServiceListeners(List.of(new ChatMemoryChatServiceListener(chatHistory),
						new VectorStoreChatMemoryChatServiceListener(vectorStore,
								Map.of(TransformerContentType.LONG_TERM_MEMORY, ""))))
				.build();
		}

		// @Bean
		// public StreamingChatService memoryStreamingChatAgent(OpenAiChatClient
		// streamingChatClient,
		// VectorStore vectorStore, TokenCountEstimator tokenCountEstimator, ChatHistory
		// chatHistory) {

		// return StreamingPromptTransformingChatService.builder(streamingChatClient)
		// .withRetrievers(List.of(new ChatHistoryRetriever(chatHistory), new
		// DocumentChatHistoryRetriever(vectorStore, 10)))
		// .withDocumentPostProcessors(List.of(new
		// LastMaxTokenSizeContentTransformer(tokenCountEstimator, 1000)))
		// .withAugmentors(List.of(new TextChatHistoryAugmenter()))
		// .withChatAgentListeners(List.of(new ChatHistoryAgentListener(chatHistory), new
		// DocumentChatHistoryAgentListener(vectorStore)))
		// .build();
		// }

		@Bean
		public RelevancyEvaluator relevancyEvaluator(OpenAiChatClient chatClient) {
			// Use GPT 4 as a better model for determining relevancy. gpt 3.5 makes basic
			// mistakes
			OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
				.withModel(GPT_4_TURBO_PREVIEW.getValue())
				.build();
			return new RelevancyEvaluator(chatClient, openAiChatOptions);
		}

	}

}
