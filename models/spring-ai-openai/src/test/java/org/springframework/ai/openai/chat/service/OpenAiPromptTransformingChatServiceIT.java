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

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import org.springframework.ai.chat.prompt.transformer.QuestionContextAugmentor;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;
import org.springframework.ai.chat.prompt.transformer.VectorStoreRetriever;
import org.springframework.ai.chat.service.ChatService;
import org.springframework.ai.chat.service.PromptTransformingChatService;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.reader.JsonReader;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_0_TURBO;

@Testcontainers
@SpringBootTest(classes = OpenAiPromptTransformingChatServiceIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiPromptTransformingChatServiceIT {

	private static final String COLLECTION_NAME = "test_collection";

	private static final int QDRANT_GRPC_PORT = 6334;

	@Container
	static QdrantContainer qdrantContainer = new QdrantContainer("qdrant/qdrant:v1.9.2");

	private final ChatModel chatModel;

	private final VectorStore vectorStore;

	@Value("classpath:/data/acme/bikes.json")
	private Resource bikesResource;

	private ChatService chatService;

	@Autowired
	public OpenAiPromptTransformingChatServiceIT(ChatModel chatModel, ChatService chatService,
			VectorStore vectorStore) {
		this.chatModel = chatModel;
		this.chatService = chatService;
		this.vectorStore = vectorStore;
	}

	@Test
	void simpleChat() {
		loadData();

		String question = "What reliable road bike?";
		var prompt = new Prompt(new UserMessage(question));
		var chatServiceResponse = this.chatService.call(new ChatServiceContext(prompt));
		String answer = chatServiceResponse.getChatResponse().getResult().getOutput().getContent();
		assertThat(answer).containsAnyOf("Celerity", "Velocity")
			.as("Answer does not include 'Celerity' or 'Velocity'.  Answer = %s", answer);

		// Use GPT 4 Turbo as a better model for determining relevancy.
		OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder().withModel(GPT_4_0_TURBO.getValue()).build();
		var relevancyEvaluator = new RelevancyEvaluator(this.chatModel, openAiChatOptions);

		EvaluationResponse evaluationResponse = relevancyEvaluator.evaluate(chatServiceResponse.toEvaluationRequest());
		assertThat(evaluationResponse.isPass())
			.as("Response is not relevant to the question.  Question = %s;  Answer = %s", question, answer);

	}

	void loadData() {
		JsonReader jsonReader = new JsonReader(bikesResource, "name", "price", "shortDescription", "description");
		var textSplitter = new TokenTextSplitter();
		List<Document> splitDocuments = textSplitter.split(jsonReader.read());

		for (Document splitDocument : splitDocuments) {
			splitDocument.getMetadata().put(TransformerContentType.EXTERNAL_KNOWLEDGE, "true");
		}

		vectorStore.write(splitDocuments);
	}

	void loadData2() {
		JsonReader jsonReader = null;
		TokenTextSplitter tokenTextSplitter = null;
		VectorStore vectorStore = null;

		List<Document> documents = jsonReader.read();
		List<Document> splitDocuments = tokenTextSplitter.split(documents);
		vectorStore.write(splitDocuments);

		// Now in java.util.Function style.

		Supplier<List<Document>> docs = jsonReader::read;

	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public ChatModel openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatModel(openAiApi);
		}

		@Bean
		public EmbeddingModel embeddingModel(OpenAiApi openAiApi) {
			return new OpenAiEmbeddingModel(openAiApi);
		}

		@Bean
		public VectorStore qdrantVectorStore(EmbeddingModel embeddingModel) {
			QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient
				.newBuilder(qdrantContainer.getHost(), qdrantContainer.getMappedPort(QDRANT_GRPC_PORT), false)
				.build());
			return new QdrantVectorStore(qdrantClient, COLLECTION_NAME, embeddingModel, true);
		}

		@Bean
		public ChatService chatService(ChatModel chatModel, VectorStore vectorStore) {
			return PromptTransformingChatService.builder(chatModel)
				.withRetrievers(List.of(new VectorStoreRetriever(vectorStore, SearchRequest.defaults())))
				.withAugmentors(List.of(new QuestionContextAugmentor()))
				.build();

		}

	}

}
