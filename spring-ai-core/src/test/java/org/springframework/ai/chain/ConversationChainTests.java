package org.springframework.ai.chain;

import static org.assertj.core.api.Assertions.assertThat;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.memory.ConversationBufferMemory;
import org.springframework.ai.parser.OutputParser;
import org.springframework.ai.parser.StringOutputParser;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class ConversationChainTests {

    @Test
    public void test() {
        AiClient aiClient = mock(AiClient.class);
        Mockito.when(aiClient.generate(Mockito.any(Prompt.class))).thenReturn(new AiResponse(
                List.of(new Generation("The sky is blue because blue light is scattered more than other colors."))));

        PromptTemplate promptTemplate = new PromptTemplate("Answer this question submitted by the user: {question}\nHISTORY: {history}");
        String inputKey = "question";
        String outputKey = "response";
        OutputParser outputParser = new StringOutputParser();

        ConversationChain chain = new ConversationChain(aiClient, promptTemplate, inputKey, outputKey, outputParser);
        ConversationBufferMemory memory = new ConversationBufferMemory();
        chain.setMemory(memory);

        AiOutput output = chain.apply(new AiInput(Map.of("question", "Why is the sky blue?")));
        Map<String, Object> outputData = output.getOutputData();
        assertThat(outputData.get("response")).isEqualTo("The sky is blue because blue light is scattered more than other colors.");
        Map<String, Object> memoryMap = memory.load(Map.of());
        assertThat(memoryMap.get("history")).isEqualTo("""
                user: Why is the sky blue?
                assistant: The sky is blue because blue light is scattered more than other colors.""");

        output = chain.apply(new AiInput(Map.of("question", "What color is the sky?")));
        outputData = output.getOutputData();
        assertThat(outputData.get("response")).isEqualTo("The sky is blue because blue light is scattered more than other colors.");
        memoryMap = memory.load(Map.of());
        assertThat(memoryMap.get("history")).isEqualTo("""
                user: Why is the sky blue?
                assistant: The sky is blue because blue light is scattered more than other colors.
                user: What color is the sky?
                assistant: The sky is blue because blue light is scattered more than other colors.""");
    }

}
