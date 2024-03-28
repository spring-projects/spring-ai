package org.springframework.ai.ollama.metadata;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.util.Assert;

/**
 * {@link ChatResponseMetadata} implementation for {@literal Ollama}
 *
 * @see ChatResponseMetadata
 * @author Fu Cheng
 */
public class OllamaChatResponseMetadata implements ChatResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, usage: %2$s, rateLimit: %3$s }";

	public static OllamaChatResponseMetadata from(OllamaApi.ChatResponse response) {
		Assert.notNull(response, "OllamaApi.ChatResponse must not be null");
		Usage usage = OllamaUsage.from(response);
		return new OllamaChatResponseMetadata(usage);
	}

	private final Usage usage;

	protected OllamaChatResponseMetadata(Usage usage) {
		this.usage = usage;
	}

	@Override
	public Usage getUsage() {
		return this.usage;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getTypeName(), getUsage(), getRateLimit());
	}

}
