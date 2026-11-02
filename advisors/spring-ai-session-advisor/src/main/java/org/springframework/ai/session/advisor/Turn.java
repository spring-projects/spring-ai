/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.session.advisor;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * A call's turn as the {@link SessionTurnAdvisor} begins it: the run id it runs under
 * and, when the turn has its response before the chain runs, that response, returned in
 * place of the chain's. A resumed call whose allowed tool returned directly has one, and
 * so has a continued turn whose model had already answered when it was stopped.
 *
 * @param runId the run the turn runs under
 * @param directResponse the response to return without running the chain; {@code null}
 * when the chain runs
 * @author Christian Tzolov
 * @since 2.1.0
 */
record Turn(String runId, @Nullable ChatResponse directResponse) {

	/** A turn whose chain runs. */
	static Turn of(String runId) {
		return new Turn(runId, null);
	}

	/** The direct response as the call's response, with the run id in its context. */
	@Nullable ChatClientResponse direct(ChatClientRequest request) {
		if (this.directResponse == null) {
			return null;
		}
		return ChatClientResponse.builder()
			.chatResponse(this.directResponse)
			.context(SessionContexts.withRunId(request, this.runId).context())
			.build();
	}

}
