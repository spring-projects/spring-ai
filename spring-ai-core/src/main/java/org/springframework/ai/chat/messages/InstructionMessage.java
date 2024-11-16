package org.springframework.ai.chat.messages;

/**
 * Instructions are a subtype of user messages that will be added to each conversation in
 * AI agent scenarios, allowing AI agents to clearly understand their responsibilities
 *
 * @author wanglei
 */
public class InstructionMessage extends UserMessage {

	public InstructionMessage(String instruction) {
		super(instruction);
	}

	@Override
	public String toString() {
		return "InstructionMessage{" + "messageType=" + messageType + ", textContent='" + textContent + '\''
				+ ", metadata=" + metadata + '}';
	}

}
