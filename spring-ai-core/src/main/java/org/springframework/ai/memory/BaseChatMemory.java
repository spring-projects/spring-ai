package org.springframework.ai.memory;

import org.springframework.ai.memory.histories.BaseChatMessageHistory;
import org.springframework.ai.prompt.messages.Message;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for chat memory.
 *
 * @author Craig Walls
 */
public abstract class BaseChatMemory implements Memory {

    BaseChatMessageHistory chatMemory = new BaseChatMessageHistory(){};

    boolean returnMessages = false;

    private String inputKey;

    private String outputKey;

    @Override
    public void save(Map<String, Object> inputs, Map<String, Object> outputs) {
        String promptInputKey = inputKey;
        if (promptInputKey == null) {
            if (inputs.isEmpty()) {
                throw new IllegalStateException("One input key expected, but got none");
            }
            promptInputKey = inputs.keySet().iterator().next();
        }
        chatMemory.addUserMessage(inputs.get(promptInputKey).toString());

        String promptOutputKey = outputKey;
        if (promptOutputKey == null) {
            if (outputs.isEmpty()) {
                throw new IllegalStateException("One output key expected, but got none");
            }
            promptOutputKey = outputs.keySet().iterator().next();
        }
        chatMemory.addAiMessage(outputs.get(promptOutputKey).toString());
    }

    protected List<Message> getMessages() {
        return chatMemory.messages;
    }

    public void clear() {
        chatMemory.clear();
    }

}
