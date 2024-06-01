package org.springframework.ai.dashscope.metadata;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.dashscope.record.TokenUsage;

import java.util.HashMap;

/**
 * @author Nottyjay Ji
 */
public class DashscopeChatResponseMetadata extends HashMap<String, Object> implements ChatResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, usage: %3$s }";

	private final TokenUsage usage;

	private final String requestId;

	public DashscopeChatResponseMetadata(TokenUsage usage, String requestId) {
		this.usage = usage;
		this.requestId = requestId;
	}

	public static DashscopeChatResponseMetadata from(TokenUsage usage, String requestId) {
		return new DashscopeChatResponseMetadata(usage, requestId);
	}

	public String getRequestId() {
		return this.requestId;
	}

	public TokenUsage getTokenUsage() {
		return usage != null ? usage : new TokenUsage(0, 0, 0);
	}

	@Override
	public Usage getUsage() {
		return getTokenUsage();
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getRequestId(), getTokenUsage());
	}

}
