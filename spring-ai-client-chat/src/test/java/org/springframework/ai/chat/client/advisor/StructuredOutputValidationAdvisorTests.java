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

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import io.micrometer.observation.ObservationRegistry;
import io.modelcontextprotocol.json.TypeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StructuredOutputValidationAdvisor}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class StructuredOutputValidationAdvisorTests {

	@Mock
	private CallAdvisorChain callAdvisorChain;

	@Mock
	private StreamAdvisorChain streamAdvisorChain;

	@Test
	void whenOutputTypeIsNullThenThrow() {
		assertThatThrownBy(() -> StructuredOutputValidationAdvisor.builder().build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("outputType must be set");
	}

	@Test
	void whenAdvisorOrderIsOutOfRangeThenThrow() {
		assertThatThrownBy(() -> StructuredOutputValidationAdvisor.builder().outputType(new TypeRef<Person>() {
		}).advisorOrder(Ordered.HIGHEST_PRECEDENCE).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");

		assertThatThrownBy(() -> StructuredOutputValidationAdvisor.builder().outputType(new TypeRef<Person>() {
		}).advisorOrder(Ordered.LOWEST_PRECEDENCE).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");
	}

	@Test
	void whenRepeatAttemptsIsNegativeThenThrow() {
		assertThatThrownBy(() -> StructuredOutputValidationAdvisor.builder().outputType(new TypeRef<Person>() {
		}).repeatAttempts(-1).build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("repeatAttempts must be greater than or equal to 0");
	}

	@Test
	void testBuilderMethodChainingWithTypeRef() {
		TypeRef<Person> typeRef = new TypeRef<>() {
		};
		int customOrder = Ordered.HIGHEST_PRECEDENCE + 500;
		int customAttempts = 5;

		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(typeRef)
			.advisorOrder(customOrder)
			.repeatAttempts(customAttempts)
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
		assertThat(advisor.getName()).isEqualTo("Structured Output Validation Advisor");
	}

	@Test
	void testBuilderMethodChainingWithTypeReference() {
		TypeReference<Person> typeReference = new TypeReference<>() {
		};
		int customOrder = Ordered.HIGHEST_PRECEDENCE + 600;

		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(typeReference)
			.advisorOrder(customOrder)
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
		assertThat(advisor.getName()).isEqualTo("Structured Output Validation Advisor");
	}

	@Test
	void testBuilderMethodChainingWithParameterizedTypeReference() {
		ParameterizedTypeReference<Person> parameterizedTypeReference = new ParameterizedTypeReference<>() {
		};
		int customOrder = Ordered.HIGHEST_PRECEDENCE + 700;

		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(parameterizedTypeReference)
			.advisorOrder(customOrder)
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
		assertThat(advisor.getName()).isEqualTo("Structured Output Validation Advisor");
	}

	@Test
	void testDefaultValues() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 2000);
		assertThat(advisor.getName()).isEqualTo("Structured Output Validation Advisor");
	}

	@Test
	void whenChatClientRequestIsNullThenThrow() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.build();

		assertThatThrownBy(() -> advisor.adviseCall(null, this.callAdvisorChain))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientRequest must not be null");
	}

	@Test
	void whenCallAdvisorChainIsNullThenThrow() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.build();
		ChatClientRequest request = createMockRequest();

		assertThatThrownBy(() -> advisor.adviseCall(request, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("callAdvisorChain must not be null");
	}

	@Test
	void testAdviseCallWithValidJsonOnFirstAttempt() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.repeatAttempts(3)
			.build();

		ChatClientRequest request = createMockRequest();
		String validJson = "{\"name\":\"John Doe\",\"age\":30}";
		ChatClientResponse validResponse = createMockResponse(validJson);

		// Create a terminal advisor that returns the valid response
		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new CallAdvisor() {
			@Override
			public String getName() {
				return "terminal";
			}

			@Override
			public int getOrder() {
				return Ordered.LOWEST_PRECEDENCE;
			}

			@Override
			public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
				callCount[0]++;
				return validResponse;
			}
		};

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = realChain.nextCall(request);

		assertThat(result).isEqualTo(validResponse);
		assertThat(callCount[0]).isEqualTo(1);
	}

	@Test
	void testAdviseCallWithInvalidJsonRetries() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.repeatAttempts(2)
			.build();

		ChatClientRequest request = createMockRequest();
		String invalidJson = "{\"name\":\"John Doe\"}"; // Missing required 'age' field
		String validJson = "{\"name\":\"John Doe\",\"age\":30}";
		ChatClientResponse invalidResponse = createMockResponse(invalidJson);
		ChatClientResponse validResponse = createMockResponse(validJson);

		// Create a terminal advisor that returns invalid response first, then valid
		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new CallAdvisor() {
			@Override
			public String getName() {
				return "terminal";
			}

			@Override
			public int getOrder() {
				return Ordered.LOWEST_PRECEDENCE;
			}

			@Override
			public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
				callCount[0]++;
				return callCount[0] == 1 ? invalidResponse : validResponse;
			}
		};

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = realChain.nextCall(request);

		assertThat(result).isEqualTo(validResponse);
		assertThat(callCount[0]).isEqualTo(2);
	}

	@Test
	void testAdviseCallExhaustsAllRetries() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.repeatAttempts(2)
			.build();

		ChatClientRequest request = createMockRequest();
		String invalidJson = "{\"invalid\":\"json\"}";
		ChatClientResponse invalidResponse = createMockResponse(invalidJson);

		// Create a terminal advisor that always returns invalid response
		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new CallAdvisor() {
			@Override
			public String getName() {
				return "terminal";
			}

			@Override
			public int getOrder() {
				return Ordered.LOWEST_PRECEDENCE;
			}

			@Override
			public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
				callCount[0]++;
				return invalidResponse;
			}
		};

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = realChain.nextCall(request);

		assertThat(result).isEqualTo(invalidResponse);
		// Initial attempt + 2 retries = 3 total calls
		assertThat(callCount[0]).isEqualTo(3);
	}

	@Test
	void testAdviseCallWithZeroRetries() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.repeatAttempts(0)
			.build();

		ChatClientRequest request = createMockRequest();
		String invalidJson = "{\"invalid\":\"json\"}";
		ChatClientResponse invalidResponse = createMockResponse(invalidJson);

		// Create a terminal advisor
		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new CallAdvisor() {
			@Override
			public String getName() {
				return "terminal";
			}

			@Override
			public int getOrder() {
				return Ordered.LOWEST_PRECEDENCE;
			}

			@Override
			public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
				callCount[0]++;
				return invalidResponse;
			}
		};

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = realChain.nextCall(request);

		assertThat(result).isEqualTo(invalidResponse);
		// Only initial attempt, no retries
		assertThat(callCount[0]).isEqualTo(1);
	}

	@Test
	void testAdviseCallWithNullChatResponse() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.repeatAttempts(1)
			.build();

		ChatClientRequest request = createMockRequest();
		ChatClientResponse nullResponse = mock(ChatClientResponse.class);
		when(nullResponse.chatResponse()).thenReturn(null);

		String validJson = "{\"name\":\"John Doe\",\"age\":30}";
		ChatClientResponse validResponse = createMockResponse(validJson);

		// Create a terminal advisor that returns null response first, then valid
		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new CallAdvisor() {
			@Override
			public String getName() {
				return "terminal";
			}

			@Override
			public int getOrder() {
				return Ordered.LOWEST_PRECEDENCE;
			}

			@Override
			public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
				callCount[0]++;
				return callCount[0] == 1 ? nullResponse : validResponse;
			}
		};

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = realChain.nextCall(request);

		assertThat(result).isEqualTo(validResponse);
		assertThat(callCount[0]).isEqualTo(2);
	}

	@Test
	void testAdviseCallWithNullResult() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.repeatAttempts(1)
			.build();

		ChatClientRequest request = createMockRequest();
		ChatResponse chatResponse = mock(ChatResponse.class);
		when(chatResponse.getResult()).thenReturn(null);
		ChatClientResponse nullResultResponse = mock(ChatClientResponse.class);
		when(nullResultResponse.chatResponse()).thenReturn(chatResponse);

		String validJson = "{\"name\":\"John Doe\",\"age\":30}";
		ChatClientResponse validResponse = createMockResponse(validJson);

		// Create a terminal advisor
		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new CallAdvisor() {
			@Override
			public String getName() {
				return "terminal";
			}

			@Override
			public int getOrder() {
				return Ordered.LOWEST_PRECEDENCE;
			}

			@Override
			public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
				callCount[0]++;
				return callCount[0] == 1 ? nullResultResponse : validResponse;
			}
		};

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = realChain.nextCall(request);

		assertThat(result).isEqualTo(validResponse);
		assertThat(callCount[0]).isEqualTo(2);
	}

	@Test
	void testAdviseCallWithComplexType() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Address>() {
			})
			.repeatAttempts(2)
			.build();

		ChatClientRequest request = createMockRequest();
		String validJson = "{\"street\":\"123 Main St\",\"city\":\"Springfield\",\"zipCode\":\"12345\"}";
		ChatClientResponse validResponse = createMockResponse(validJson);

		// Create a terminal advisor
		CallAdvisor terminalAdvisor = new CallAdvisor() {
			@Override
			public String getName() {
				return "terminal";
			}

			@Override
			public int getOrder() {
				return Ordered.LOWEST_PRECEDENCE;
			}

			@Override
			public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
				return validResponse;
			}
		};

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = realChain.nextCall(request);

		assertThat(result).isEqualTo(validResponse);
	}

	@Test
	void testAdviseStreamThrowsUnsupportedOperationException() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.build();
		ChatClientRequest request = createMockRequest();

		Flux<ChatClientResponse> result = advisor.adviseStream(request, this.streamAdvisorChain);

		assertThatThrownBy(() -> result.blockFirst()).isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("Structured Output Validation Advisor does not support streaming");
	}

	@Test
	void testGetName() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.build();
		assertThat(advisor.getName()).isEqualTo("Structured Output Validation Advisor");
	}

	@Test
	void testGetOrder() {
		int customOrder = Ordered.HIGHEST_PRECEDENCE + 1500;
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.advisorOrder(customOrder)
			.build();

		assertThat(advisor.getOrder()).isEqualTo(customOrder);
	}

	@Test
	void testMultipleRetriesWithDifferentInvalidResponses() {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(new TypeRef<Person>() {
			})
			.repeatAttempts(3)
			.build();

		ChatClientRequest request = createMockRequest();
		String invalidJson1 = "{\"name\":\"John\"}"; // Missing age
		String invalidJson2 = "{\"age\":30}"; // Missing name
		String invalidJson3 = "not json at all";
		String validJson = "{\"name\":\"John Doe\",\"age\":30}";

		ChatClientResponse invalidResponse1 = createMockResponse(invalidJson1);
		ChatClientResponse invalidResponse2 = createMockResponse(invalidJson2);
		ChatClientResponse invalidResponse3 = createMockResponse(invalidJson3);
		ChatClientResponse validResponse = createMockResponse(validJson);

		// Create a terminal advisor that cycles through invalid responses
		int[] callCount = { 0 };
		CallAdvisor terminalAdvisor = new CallAdvisor() {
			@Override
			public String getName() {
				return "terminal";
			}

			@Override
			public int getOrder() {
				return Ordered.LOWEST_PRECEDENCE;
			}

			@Override
			public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
				callCount[0]++;
				return switch (callCount[0]) {
					case 1 -> invalidResponse1;
					case 2 -> invalidResponse2;
					case 3 -> invalidResponse3;
					default -> validResponse;
				};
			}
		};

		CallAdvisorChain realChain = DefaultAroundAdvisorChain.builder(ObservationRegistry.NOOP)
			.pushAll(List.of(advisor, terminalAdvisor))
			.build();

		ChatClientResponse result = realChain.nextCall(request);

		assertThat(result).isEqualTo(validResponse);
		assertThat(callCount[0]).isEqualTo(4);
	}

	// Helper methods

	private ChatClientRequest createMockRequest() {
		Prompt prompt = new Prompt(List.of(new UserMessage("test message")));
		return ChatClientRequest.builder().prompt(prompt).build();
	}

	private ChatClientResponse createMockResponse(String jsonOutput) {
		AssistantMessage assistantMessage = new AssistantMessage(jsonOutput);
		Generation generation = new Generation(assistantMessage);
		ChatResponse chatResponse = new ChatResponse(List.of(generation));

		ChatClientResponse response = mock(ChatClientResponse.class);
		when(response.chatResponse()).thenReturn(chatResponse);

		return response;
	}

	// Test DTOs
	public static class Person {

		private String name;

		private int age;

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getAge() {
			return this.age;
		}

		public void setAge(int age) {
			this.age = age;
		}

	}

	public static class Address {

		private String street;

		private String city;

		private String zipCode;

		public String getStreet() {
			return this.street;
		}

		public void setStreet(String street) {
			this.street = street;
		}

		public String getCity() {
			return this.city;
		}

		public void setCity(String city) {
			this.city = city;
		}

		public String getZipCode() {
			return this.zipCode;
		}

		public void setZipCode(String zipCode) {
			this.zipCode = zipCode;
		}

	}

}
