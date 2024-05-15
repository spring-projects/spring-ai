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

package org.springframework.ai.openai.chat.chatbot;

import java.util.List;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.chatbot.ChatBot;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.chatbot.DefaultChatBot;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.transformer.PromptContext;
import org.springframework.ai.chat.prompt.transformer.QuestionContextAugmentor;
import org.springframework.ai.chat.prompt.transformer.VectorStoreRetriever;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_TURBO_PREVIEW;

@Testcontainers
@SpringBootTest(classes = OpenAiDefaultChatBotIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiDefaultChatBotIT {

	private static final String COLLECTION_NAME = "test_collection";

	private static final int QDRANT_GRPC_PORT = 6334;

	@Container
	static QdrantContainer qdrantContainer = new QdrantContainer("qdrant/qdrant:v1.9.2");

	private final ChatClient chatClient;

	private final VectorStore vectorStore;

	@Value("classpath:/data/acme/bikes.json")
	private Resource bikesResource;

	private ChatBot chatBot;

	@Autowired
	public OpenAiDefaultChatBotIT(ChatClient chatClient, ChatBot chatBot, VectorStore vectorStore) {
		this.chatClient = chatClient;
		this.chatBot = chatBot;
		this.vectorStore = vectorStore;
	}

	@Test
	void simpleChat() {
		loadData();

		var prompt = new Prompt(new UserMessage("What reliable road bike?"));
		var chatBotResponse = this.chatBot.call(new PromptContext(prompt));
		String answer = chatBotResponse.getChatResponse().getResult().getOutput().getContent();
		assertTrue(answer.contains("Celerity"), "Response does not include 'Celerity'");

		// Use GPT 4 as a better model for determining relevancy. gpt 3.5 makes basic
		// mistakes
		OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
			.withModel(GPT_4_TURBO_PREVIEW.getValue())
			.build();
		var relevancyEvaluator = new RelevancyEvaluator(this.chatClient, openAiChatOptions);
		EvaluationRequest evaluationRequest = new EvaluationRequest(chatBotResponse);
		EvaluationResponse evaluationResponse = relevancyEvaluator.evaluate(evaluationRequest);
		assertTrue(evaluationResponse.isPass(), "Response is not relevant to the question");

	}

	void loadData() {
		JsonReader jsonReader = new JsonReader(bikesResource, "name", "price", "shortDescription", "description");
		var textSplitter = new TokenTextSplitter();
		List<Document> splitDocuments = textSplitter.apply(jsonReader.get());

		for (Document splitDocument : splitDocuments) {
			splitDocument.getMetadata().put(TransformerContentType.EXTERNAL_KNOWLEDGE, "true");
		}

		vectorStore.accept(splitDocuments);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public ChatClient openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatClient(openAiApi);
		}

		@Bean
		public EmbeddingClient embeddingClient(OpenAiApi openAiApi) {
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
		public ChatBot chatBot(ChatClient chatClient, VectorStore vectorStore) {
			return DefaultChatBot.builder(chatClient)
				.withRetrievers(List.of(new VectorStoreRetriever(vectorStore, SearchRequest.defaults())))
				.withAugmentors(List.of(new QuestionContextAugmentor()))
				.build();

		}

	}

}
