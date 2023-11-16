package org.springframework.ai.client;

import org.springframework.ai.document.Document;
import org.springframework.ai.memory.Memory;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultAiThing implements AiThing {

	private final AiClient aiClient;

	private PromptTemplate promptTemplate;

	private VectorStore vectorStore;

	private String vectorStoreKey;

	private Memory memory;

	protected DefaultAiThing(AiClient aiClient, PromptTemplate promptTemplate) {
		this.aiClient = aiClient;
		this.promptTemplate = promptTemplate;
	}

	public AiThing promptTemplate(String promptTemplate) {
		this.promptTemplate = new PromptTemplate(promptTemplate);
		return this;
	}

	public AiThing vectorStore(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
		return this;
	}

	public AiThing vectorStoreKey(String vectorStoreKey) {
		this.vectorStoreKey = vectorStoreKey;
		return this;
	}

	public AiThing conversationMemory(Memory memory) {
		this.memory = memory;
		return this;
	}

	@Override
	public String generate() {
		return generate(Map.of());
	}

	@Override
	public String generate(Map<String, Object> parameters) {
		Map<String, Object> resolvedParameters = new HashMap<>(parameters);

		if (vectorStore != null) {
			// TODO: There is a lot of hardcoding here. Need to make this more flexible.
			String input = memory != null ? generateStandaloneQuestion(parameters) : parameters.get("input").toString();

			List<Document> documents = vectorStore.similaritySearch(input, 2);
			List<String> contentList = documents.stream().map(doc -> {
				return doc.getContent() + "\n";
			}).toList();
			resolvedParameters.put("input", input); // replace original question with
													// standalone question
			resolvedParameters.put(vectorStoreKey, contentList);
		}
		else {
			resolvedParameters = preProcess(resolvedParameters);
		}

		PromptTemplate promptTemplateCopy = new PromptTemplate(promptTemplate.getTemplate());
		String prompt = promptTemplateCopy.render(resolvedParameters);
		AiResponse aiResponse = aiClient.generate(new Prompt(prompt));
		String generationResponse = aiResponse.getGenerations().get(0).getText();

		// post-process memory
		postProcess(parameters, aiResponse);

		return generationResponse;
	}

	private String generateStandaloneQuestion(Map<String, Object> parameters) {
		Map<String, Object> resolvedParameters = new HashMap<>(parameters);
		resolvedParameters = preProcess(resolvedParameters);

		PromptTemplate standalonePromptTemplate = new PromptTemplate(
				DefaultPromptTemplateStrings.STANDALONE_QUESTION_PROMPT);
		String prompt = standalonePromptTemplate.render(resolvedParameters);
		AiResponse aiResponse = aiClient.generate(new Prompt(prompt));
		return aiResponse.getGenerations().get(0).getText();
	}

	private Map<String, Object> preProcess(Map<String, Object> parameters) {
		Map<String, Object> combinedParameters = new HashMap<>(parameters);
		if (memory != null) {
			Map<String, Object> externalContext = memory.load(parameters);
			Object history = externalContext.get("history");
			combinedParameters.putAll(externalContext);
		}
		return combinedParameters;
	}

	private void postProcess(Map<String, Object> parameters, AiResponse aiResponse) {
		if (memory != null) {
			memory.save(parameters, Map.of("history", aiResponse.getGenerations().get(0).getText()));
		}
	}

}
