package org.springframework.ai.chat.client.advisor.api;

import reactor.core.publisher.Flux;

public interface StreamAroundAdvisorChain {

	Flux<AdvisedResponse> nextAroundStream(AdvisedRequest advisedRequest);

}