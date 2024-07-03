package org.springframework.ai.mistralai.metadata;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.util.Assert;

import java.util.HashMap;

/**
 * {@link ChatResponseMetadata} implementation for {@literal Mistral AI}.
 *
 * @author Thomas Vitale
 * @see ChatResponseMetadata
 * @see Usage
 * @since 1.0.0
 */
public class MistralAiChatResponseMetadata extends HashMap<String, Object> implements ChatResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, model: %3$s, usage: %4$s }";

	public static MistralAiChatResponseMetadata from(MistralAiApi.ChatCompletion result) {
		Assert.notNull(result, "Mistral AI ChatCompletion must not be null");
		MistralAiUsage usage = MistralAiUsage.from(result.usage());
		return new MistralAiChatResponseMetadata(result.id(), result.model(), usage);
	}

	private final String id;

	private final String model;

	private final Usage usage;

	protected MistralAiChatResponseMetadata(String id, String model, MistralAiUsage usage) {
		this.id = id;
		this.model = model;
		this.usage = usage;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public Usage getUsage() {
		Usage usage = this.usage;
		return usage != null ? usage : new EmptyUsage();
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getId(), getModel(), getUsage());
	}

}
