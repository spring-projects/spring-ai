package org.springframework.ai.chain;

import org.junit.jupiter.api.Test;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.parser.OutputParser;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.ai.prompt.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

public class AiChainTests {

    @Test
    public void x() {
        AiClient aiClient = mock(AiClient.class);
        Prompt prompt = new Prompt(new UserMessage("Tell me a joke about cows"));
        AiResponse response = new AiResponse(List.of(new Generation("Why did the cow cross the road? To get to the udder side.")));
        when(aiClient.generate(prompt)).thenReturn(response);

        // String resource for prompt string
        PromptTemplate promptTemplate = new PromptTemplate("Tell me a joke about {subject}");

        OutputParser outputParser = mock(OutputParser.class);

        AiChain aiChain = new AiChain(aiClient, promptTemplate, "outdata", outputParser);

        AiInput aiInput = new AiInput(Map.of("subject", "cows"));
        AiOutput aiOutput = aiChain.apply(aiInput);
        assertThat(aiOutput.getOutputData().get("outdata")).isEqualTo("Why did the cow cross the road? To get to the udder side.");
    }

}
