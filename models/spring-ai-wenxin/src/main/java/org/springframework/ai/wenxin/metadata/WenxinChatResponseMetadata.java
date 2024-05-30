package org.springframework.ai.wenxin.metadata;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.HashMap;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午5:02
 * @description:
 */
public class WenxinChatResponseMetadata extends HashMap<String, Object> implements ChatResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, usage: %3$s, rateLimit: %4$s }";

	private final String id;

	private final Usage usage;

	@Nullable
	private RateLimit rateLimit;

	protected WenxinChatResponseMetadata(String id, WenxinUsage usage) {
		this(id, usage, null);
	}

	protected WenxinChatResponseMetadata(String id, WenxinUsage usage, @Nullable WenxinRateLimit rateLimit) {
		this.id = id;
		this.usage = usage;
		this.rateLimit = rateLimit;
	}

	public static WenxinChatResponseMetadata from(WenxinApi.ChatCompletion result) {
		Assert.notNull(result, "Wenxin ChatCompletionResult must not be null");
		WenxinUsage usage = WenxinUsage.from(result.usage());
		WenxinChatResponseMetadata chatResponseMetadata = new WenxinChatResponseMetadata(result.id(), usage);
		return chatResponseMetadata;
	}

	public String getId() {
		return this.id;
	}

	@Override
	@Nullable
	public RateLimit getRateLimit() {
		RateLimit rateLimit = this.rateLimit;
		return rateLimit != null ? rateLimit : new EmptyRateLimit();
	}

	@Override
	public Usage getUsage() {
		Usage usage = this.usage;
		return usage != null ? usage : new EmptyUsage();
	}

	public WenxinChatResponseMetadata withRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
		return this;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getId(), getUsage(), getRateLimit());
	}

}
