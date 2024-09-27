package org.springframework.ai.chat.client.advisor.api;

public interface CallAroundAdvisorChain {

	AdvisedResponse nextAroundCall(AdvisedRequest advisedRequest);

}