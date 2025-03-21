package org.springframework.ai.chat.client.advisor.api;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.util.function.Predicate;

/**
 * A stream utility class to provide support methods handling {@link AdvisedResponse}.
 */
public final class AdvisedResponseStreamUtils {

	/**
	 * Returns a predicate that checks whether the provided {@link AdvisedResponse}
	 * contains a {@link ChatResponse} with at least one result having a non-empty finish
	 * reason in its metadata.
	 * @return a {@link Predicate} that evaluates whether the finish reason exists within
	 * the response metadata.
	 */
	public static Predicate<AdvisedResponse> onFinishReason() {
		return advisedResponse -> {
			ChatResponse chatResponse = advisedResponse.response();
			return chatResponse != null && chatResponse.getResults() != null
					&& chatResponse.getResults()
						.stream()
						.anyMatch(result -> result != null && result.getMetadata() != null
								&& StringUtils.hasText(result.getMetadata().getFinishReason()));
		};
	}

}
