package org.springframework.ai.chat.client.advisor.around;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.CollectionUtils;

import reactor.core.publisher.Flux;

/**
 * A {@link CallAroundAdvisor} and {@link StreamAroundAdvisor} that filters out the
 * response if the user input contains any of the sensitive words.
 * 
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class SafeGuardAroundAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

	private final List<String> sensitiveWords;

	public SafeGuardAroundAdvisor(List<String> sensitiveWords) {
		this.sensitiveWords = sensitiveWords;
	}

	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public ChatResponse aroundCall(AdvisedRequest advisedRequest, Map<String, Object> adviceContext,
			AroundAdvisorChain chain) {

		if (!CollectionUtils.isEmpty(this.sensitiveWords)
				&& sensitiveWords.stream().anyMatch(w -> advisedRequest.userText().contains(w))) {
			return ChatResponse.builder().withGenerations(List.of()).build();
		}

		return chain.nextAroundCall(advisedRequest, adviceContext);
	}

	@Override
	public Flux<ChatResponse> aroundStream(AdvisedRequest advisedRequest, Map<String, Object> adviceContext,
			AroundAdvisorChain chain) {

		if (!CollectionUtils.isEmpty(this.sensitiveWords)
				&& sensitiveWords.stream().anyMatch(w -> advisedRequest.userText().contains(w))) {
			return Flux.empty();
		}

		return chain.nextAroundStream(advisedRequest, adviceContext);

	}

}