package org.springframework.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.InMemoryVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AiThingTests {

    @Test
    public void simplePrompt() {
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generate("Why is the sky blue?"))
                .thenReturn("Because of Rayleigh scattering.");

        // create and use the AiThing
        AiThing aiThing = AiThing.create(aiClient)
                .promptTemplate("Why is the sky blue?");
        String response = aiThing.generate();

        assertThat(response).isEqualTo("Because of Rayleigh scattering.");
    }

    @Test
    public void simplePromptUsingBuilder() {
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generate("Why is the sky blue?"))
                .thenReturn("Because of Rayleigh scattering.");

        // create and use the AiThing
        AiThing aiThing = AiThing.builder()
                .aiClient(aiClient)
                .promptTemplate("Why is the sky blue?")
                .build();
        String response = aiThing.generate();

        assertThat(response).isEqualTo("Because of Rayleigh scattering.");
    }

    @Test
    public void promptWithParameters() {
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generate("Tell me a joke about cows."))
                .thenReturn("What do you call a herd of wealthy cows? Cash cows.");

        // create and use the AiThing
        AiThing aiThing = AiThing.builder()
                .aiClient(aiClient)
                .promptTemplate("Tell me a joke about {subject}.")
                .build();
        String response = aiThing.generate(Map.of("subject", "cows"));

        assertThat(response).isEqualTo("What do you call a herd of wealthy cows? Cash cows.");
    }

    @Test
    public void usingVectorStore() {
        // Create a template to work from
        String ragTemplate = """
You're assisting with questions about a specific board game.
Use the information from the DOCUMENTS section to provide accurate answers.
If unsure, simply state that you don't know.

QUESTION:
{question}

DOCUMENTS:
{documents}
""";

        // Mock the vector store
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch("How do you score roads?", 2))
                .thenReturn(List.of(
                        new Document("Roads are scored when completed and are worth 1 point per tile they go through.", Map.of()),
                        new Document("Roads are terminated at cities, monasteries, and crossroads.", Map.of())
                ));

        // Create a resolved version of the template for mocking purposes only
        String resolvedRagTemplate = ragTemplate.replace("{documents}", """
Roads are scored when completed and are worth 1 point per tile they go through.
Roads are terminated at cities, monasteries, and crossroads.
""").replace("{question}", "How do you score roads?");

        // Mock the AiClient
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generate(resolvedRagTemplate))
                .thenReturn("Roads are worth 1 point for each tile of a completed road.");

        // Build the AiThing
        AiThing aiThing = AiThing.builder()
                .aiClient(aiClient)
                .promptTemplate(ragTemplate)
                .vectorStore(vectorStore)
                .build();

        // Ask the question
        String response = aiThing.generate(Map.of("question", "How do you score roads?"));

        // Assert the response
        assertThat(response).isEqualTo("Roads are worth 1 point for each tile of a completed road.");
    }

}
