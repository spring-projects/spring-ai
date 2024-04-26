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

package org.springframework.ai.openai.chat.agent;

import java.util.List;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.agent.ChatAgent;
import org.springframework.ai.chat.agent.DefaultChatAgent;
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

@Testcontainers
@SpringBootTest(classes = OpenAiDefaultChatAgentIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiDefaultChatAgentIT {

	private static final String COLLECTION_NAME = "test_collection";

	private static final int QDRANT_GRPC_PORT = 6334;

	@Container
	static QdrantContainer qdrantContainer = new QdrantContainer("qdrant/qdrant:v1.7.4");

	private final ChatClient chatClient;

	private final VectorStore vectorStore;

	@Value("classpath:/data/acme/bikes.json")
	private Resource bikesResource;

	private ChatAgent chatAgent;

	@Autowired
	public OpenAiDefaultChatAgentIT(ChatClient chatClient, ChatAgent chatAgent, VectorStore vectorStore) {
		this.chatClient = chatClient;
		this.chatAgent = chatAgent;
		this.vectorStore = vectorStore;
	}

	@Test
	void simpleChat() {
		loadData();

		var prompt = new Prompt(new UserMessage("What bike is good for city commuting?"));
		PromptContext promptContext = new PromptContext(prompt);

		var agentResponse = this.chatAgent.call(promptContext);
		System.out.println(agentResponse.getChatResponse().getResult().getOutput().getContent());

		var relevancyEvaluator = new RelevancyEvaluator(this.chatClient);
		EvaluationRequest evaluationRequest = new EvaluationRequest(agentResponse);
		EvaluationResponse evaluationResponse = relevancyEvaluator.evaluate(evaluationRequest);

		System.out.println(evaluationResponse);

	}

	void loadData() {
		JsonReader jsonReader = new JsonReader(bikesResource, "name", "price", "shortDescription", "description");
		var textSplitter = new TokenTextSplitter();
		vectorStore.accept(textSplitter.apply(jsonReader.get()));
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
		public ChatAgent chatagent(ChatClient chatClient, VectorStore vectorStore) {
			return DefaultChatAgent.builder(chatClient)
				.withRetrievers(List.of(new VectorStoreRetriever(vectorStore, SearchRequest.defaults())))
				.withAugmentors(List.of(new QuestionContextAugmentor()))
				.build();

		}

	}

}
