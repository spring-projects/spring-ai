/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAroundAdvisorChain}.
 *
 * @author Thomas Vitale
 */
class DefaultAroundAdvisorChainTests {

	@Test
	void whenObservationRegistryIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultAroundAdvisorChain.builder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("the observationRegistry must be non-null");
	}

	@Test
	void whenAdvisorIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP).push(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("the advisor must be non-null");
	}

	@Test
	void whenAdvisorListIsNullThenThrow() {
		assertThatThrownBy(() -> DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP).pushAll(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("the advisors must be non-null");
	}

	@Test
	void whenAdvisorListContainsNullElementsThenThrow() {
		List<Advisor> advisors = new ArrayList<>();
		advisors.add(null);
		assertThatThrownBy(() -> DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP).pushAll(advisors).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("the advisors must not contain null elements");
	}

	@Test
	void getObservationRegistry() {
		ObservationRegistry observationRegistry = ObservationRegistry.create();
		AdvisorChain chain = DefaultAroundAdvisorChain.builder(observationRegistry).build();
		assertThat(chain.getObservationRegistry()).isEqualTo(observationRegistry);
	}

	@Test
	void getCallAdvisors() {
		CallAdvisor mockAdvisor1 = mock(CallAdvisor.class);
		when(mockAdvisor1.getName()).thenReturn("advisor1");
		when(mockAdvisor1.adviseCall(any(), any())).thenReturn(ChatClientResponse.builder().build());
		CallAdvisor mockAdvisor2 = mock(CallAdvisor.class);
		when(mockAdvisor2.getName()).thenReturn("advisor2");
		when(mockAdvisor2.adviseCall(any(), any())).thenReturn(ChatClientResponse.builder().build());

		List<CallAdvisor> advisors = List.of(mockAdvisor1, mockAdvisor2);
		CallAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP).pushAll(advisors).build();
		assertThat(chain.getCallAdvisors()).containsExactlyInAnyOrder(advisors.toArray(new CallAdvisor[0]));

		chain.nextCall(ChatClientRequest.builder().prompt(new Prompt("Hello")).build());
		assertThat(chain.getCallAdvisors()).containsExactlyInAnyOrder(advisors.toArray(new CallAdvisor[0]));

		chain.nextCall(ChatClientRequest.builder().prompt(new Prompt("Hello")).build());
		assertThat(chain.getCallAdvisors()).containsExactlyInAnyOrder(advisors.toArray(new CallAdvisor[0]));
	}

	@Test
	void getStreamAdvisors() {
		StreamAdvisor mockAdvisor1 = mock(StreamAdvisor.class);
		when(mockAdvisor1.getName()).thenReturn("advisor1");
		when(mockAdvisor1.adviseStream(any(), any())).thenReturn(Flux.just(ChatClientResponse.builder().build()));
		StreamAdvisor mockAdvisor2 = mock(StreamAdvisor.class);
		when(mockAdvisor2.getName()).thenReturn("advisor2");
		when(mockAdvisor2.adviseStream(any(), any())).thenReturn(Flux.just(ChatClientResponse.builder().build()));

		List<StreamAdvisor> advisors = List.of(mockAdvisor1, mockAdvisor2);
		StreamAdvisorChain chain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(advisors)
			.build();
		assertThat(chain.getStreamAdvisors()).containsExactlyInAnyOrder(advisors.toArray(new StreamAdvisor[0]));

		chain.nextStream(ChatClientRequest.builder().prompt(new Prompt("Hello")).build()).blockLast();
		assertThat(chain.getStreamAdvisors()).containsExactlyInAnyOrder(advisors.toArray(new StreamAdvisor[0]));

		chain.nextStream(ChatClientRequest.builder().prompt(new Prompt("Hello")).build()).blockLast();
		assertThat(chain.getStreamAdvisors()).containsExactlyInAnyOrder(advisors.toArray(new StreamAdvisor[0]));
	}

}
