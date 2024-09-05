/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.chat.client.advisor.api;

import reactor.core.publisher.Flux;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public abstract class AbstractBlaCallAroundAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

	public abstract AdvisedRequest doBefore(AdvisedRequest advisedRequest);

	public abstract AdvisedResponse doAfter(AdvisedResponse advisedResponse);

	public abstract Flux<AdvisedResponse> doAfterStream(Flux<AdvisedResponse> advisedResponse);

	@Override
	final public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, AroundAdvisorChain chain) {

		//
		advisedRequest = this.doBefore(advisedRequest);
		//

		//
		AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);
		//

		//
		var after = this.doAfter(advisedResponse);
		//

		return after;
	}

	final public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, AroundAdvisorChain chain) {

		//
		advisedRequest = this.doBefore(advisedRequest);
		//

		//
		Flux<AdvisedResponse> advisedResponse = chain.nextAroundStream(advisedRequest);
		//

		//
		var after = this.doAfterStream(advisedResponse);
		//

		return after;
	}

}
