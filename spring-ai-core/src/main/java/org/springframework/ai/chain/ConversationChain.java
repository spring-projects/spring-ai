package org.springframework.ai.chain;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.memory.ConversationBufferMemory;
import org.springframework.ai.parser.OutputParser;
import org.springframework.ai.prompt.PromptTemplate;

import java.util.List;

/**
 * Chain for conversational interaction, maintaining context in memory.
 *
 * @author Craig Walls
 */
public class ConversationChain extends AiChain {

    private final String inputKey;

    public ConversationChain(AiClient aiClient, PromptTemplate promptTemplate, String inputKey, String outputKey, OutputParser outputParser) {
        super(aiClient, promptTemplate, outputKey, outputParser);
        this.inputKey = inputKey;
        this.setMemory(new ConversationBufferMemory());
    }

    @Override
    public List<String> getInputKeys() {
        return List.of(inputKey);
    }


}
