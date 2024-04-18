package org.springframework.ai.openai.chat.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.agent.DefaultChatAgent;
import org.springframework.ai.chat.agent.PromptContext;
import org.springframework.ai.chat.agent.transformer.QAPromptContextTransformer;
import org.springframework.ai.chat.agent.transformer.VectorStorePromptContextTransformer;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
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
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.util.List;

@SpringBootTest(classes = OpenAiDefaultChatAgentIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiDefaultChatAgentIT {

	private final ChatClient chatClient;

	private final VectorStore vectorStore;

	@Value("classpath:/data/acme/bikes.json")
	private Resource bikesResource;

	@Autowired
	public OpenAiDefaultChatAgentIT(ChatClient chatClient, VectorStore vectorStore) {
		this.chatClient = chatClient;
		this.vectorStore = vectorStore;
	}

	@Test
	void simpleChat() {
		loadData();
		var chatAgent = DefaultChatAgent.builder(chatClient)
			.withRetrievers(List.of(new VectorStorePromptContextTransformer(vectorStore, SearchRequest.defaults())))
			.withAugmentors(List.of(new QAPromptContextTransformer()))
			.build();

		Prompt prompt = new Prompt(new UserMessage("What bike is good for city commuting?"));
		PromptContext promptContext = new PromptContext(prompt);

		var agentResponse = chatAgent.call(promptContext);
		System.out.println(agentResponse.getChatResponse().getResult().getOutput().getContent());

		RelevancyEvaluator relevancyEvaluator = new RelevancyEvaluator(this.chatClient);
		EvaluationRequest evaluationRequest = new EvaluationRequest(
				agentResponse.getPromptContext().getPromptHistory().get(0), agentResponse.getPromptContext().getNodes(),
				agentResponse.getChatResponse());

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
		public VectorStore vectorStore(EmbeddingClient embeddingClient) {
			return new SimpleVectorStore(embeddingClient);
		}

	}

}
