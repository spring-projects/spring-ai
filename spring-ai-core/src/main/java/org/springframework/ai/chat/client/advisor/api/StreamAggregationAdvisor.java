package org.springframework.ai.chat.client.advisor.api;

import reactor.core.publisher.Mono;

public interface StreamAggregationAdvisor extends Advisor {

	Mono<Void> accept(AdvisedResponse advisedResponse);

}
