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
            List<Document> documents = vectorStore.similaritySearch(parameters.get("input").toString(), 2); // TODO: "question" and 2 are hardcoded
            List<String> contentList = documents.stream().map(doc -> {
                return doc.getContent() + "\n";
            }).toList();
            resolvedParameters.put(vectorStoreKey, contentList);

            // TODO: Handle case where you have both a vector store *and* conversation memory
        } else {
            resolvedParameters = preProcess(resolvedParameters);
        }

        PromptTemplate promptTemplateCopy = new PromptTemplate(promptTemplate.getTemplate());
        String prompt = promptTemplateCopy.render(resolvedParameters);
        AiResponse aiResponse = aiClient.generate(new Prompt(prompt));
        System.err.println("PROMPT: " + prompt);
        String generationResponse = aiResponse.getGenerations().get(0).getText();

        // post-process memory
        postProcess(parameters, aiResponse);

        return generationResponse;
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
