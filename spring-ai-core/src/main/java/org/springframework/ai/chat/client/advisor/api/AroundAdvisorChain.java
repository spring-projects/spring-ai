package org.springframework.ai.chat.client.advisor.api;

import org.springframework.ai.chat.client.AdvisedRequest;

import reactor.core.publisher.Flux;

public interface AroundAdvisorChain {

	AdvisedResponse nextAroundCall(AdvisedRequest advisedRequest);

	Flux<AdvisedResponse> nextAroundStream(AdvisedRequest advisedRequest);

}