package org.springframework.ai.client;

import org.springframework.ai.document.Document;
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


    @Override
    public String generate() {
        return generate(Map.of());
    }

    @Override
    public String generate(Map<String, Object> parameters) {
        Map<String, Object> resolvedParameters = new HashMap<>(parameters);

        if (vectorStore != null) {
            List<Document> documents = vectorStore.similaritySearch(parameters.get("question").toString(), 2); // TODO: "question" and 2 are hardcoded
            List<String> contentList = documents.stream().map(doc -> {
                return doc.getContent() + "\n";
            }).toList();
            resolvedParameters.put(vectorStoreKey, contentList);
        }

        String prompt = promptTemplate.render(resolvedParameters);
        return aiClient.generate(prompt);
    }
}
