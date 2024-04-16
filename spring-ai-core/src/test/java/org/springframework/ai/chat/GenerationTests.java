package org.springframework.ai.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;

import static org.junit.jupiter.api.Assertions.*;

public class GenerationTests {
    @Mock
    private ChatGenerationMetadata mockChatGenerationMetadata1;

    @Mock
    private ChatGenerationMetadata mockChatGenerationMetadata2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetOutput() {
        String expectedText = "Test Assistant Message";
        Generation generation = new Generation(expectedText);
        
        assertEquals(expectedText, generation.getOutput().getContent());
    }

    @Test
    void testWithGenerationMetadata(){
        Generation generation = new Generation("Test Assistant Message");
        
        assertEquals(generation, generation.withGenerationMetadata(mockChatGenerationMetadata1));
    }

    @Test
    void testGetMetadata_Null() {
        Generation generation = new Generation("Test Assistant Message");
        ChatGenerationMetadata metadata = generation.getMetadata();
        
        assertEquals(ChatGenerationMetadata.NULL, metadata);
    }

    @Test
    void testGetMetadata_NotNull() {
        Generation generation = new Generation("Test Assistant Message");
        generation.withGenerationMetadata(mockChatGenerationMetadata1);
        ChatGenerationMetadata metadata = generation.getMetadata();
        
        assertEquals(mockChatGenerationMetadata1, metadata);
    }

    @Test
    void testEquals_SameObjects() {
        Generation generation1 = new Generation("Test Assistant Message");
        Generation generation2 = generation1;
        
        assertTrue(generation1.equals(generation2));
    }

    @Test
    void testEquals_NotInstanceOfGeneration() {
        Generation generation = new Generation("Test Assistant Message");
        Object notGenerationObject = new Object();

        assertFalse(generation.equals(notGenerationObject));
    }

    @Test
    void testEquals_SameMetadata() {
        Generation generation1 = new Generation("Test Assistant Message").withGenerationMetadata(mockChatGenerationMetadata1);
        Generation generation2 = new Generation("Test Assistant Message").withGenerationMetadata(mockChatGenerationMetadata1);

        assertTrue(generation1.equals(generation2));
    }

    @Test
    void testEquals_DifferentMetadata() {
        Generation generation1 = new Generation("Test Assistant Message").withGenerationMetadata(mockChatGenerationMetadata1);
        Generation generation2 = new Generation("Test Assistant Message").withGenerationMetadata(mockChatGenerationMetadata2);

        assertFalse(generation1.equals(generation2));
    }
}
