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

package org.springframework.ai.systemone.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.systemone.api.question.Choice;
import org.springframework.ai.systemone.api.question.Noul;
import org.springframework.ai.systemone.api.question.Score;
import org.springframework.ai.systemone.api.question.SystemOneRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

/**
 * The request bodies the client puts on the wire are compared against the examples in the
 * SystemOne API reference, verbatim.
 *
 * @author Christian Tzolov
 */
class SystemOneApiRequestTests {

	private static final String OK_BODY = """
			{"model":"jev-1.13.0","answers":{"probe":{"type":"noul","noul":0.5}},"usage":{}}""";

	private final MockSystemOneServer mock = MockSystemOneServer.create();

	@Test
	void sendsBearerTokenAndJsonContentType() {
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andExpect(method(org.springframework.http.HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
			.andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
			.andRespond(MockSystemOneServer.jsonResponse(OK_BODY));

		this.mock.client().systemOne("anything", Map.of("probe", Noul.of("Is this a probe?")));

		this.mock.server().verify();
	}

	@Test
	void sendsTheDocumentedNoulRequest() {
		expectBody("""
				{
					"state": "Help! My payouts have been failing for 3 days.",
					"model": "jev-latest",
					"questions": {
						"is_urgent": {
							"type": "noul",
							"instructions": "Does this convey urgency?",
							"criteria": {
								"true": "Explicitly time-sensitive",
								"false": "No urgency expressed"
							}
						}
					}
				}""");

		this.mock.client()
			.systemOne("Help! My payouts have been failing for 3 days.",
					Map.of("is_urgent",
							Noul.builder()
								.instructions("Does this convey urgency?")
								.whenTrue("Explicitly time-sensitive")
								.whenFalse("No urgency expressed")
								.build()));

		this.mock.server().verify();
	}

	@Test
	void sendsTheDocumentedChoiceRequest() {
		expectBody("""
				{
					"state": "Help! My payouts have been failing for 3 days.",
					"model": "jev-latest",
					"questions": {
						"department": {
							"type": "choice",
							"instructions": "Which team should handle this?",
							"criteria": {
								"billing": "Payments, invoicing, refunds",
								"technical": "Bugs, outages, integrations",
								"sales": "Pricing, upgrades, new accounts"
							}
						}
					}
				}""");

		this.mock.client()
			.systemOne("Help! My payouts have been failing for 3 days.",
					Map.of("department",
							Choice.builder()
								.instructions("Which team should handle this?")
								.option("billing", "Payments, invoicing, refunds")
								.option("technical", "Bugs, outages, integrations")
								.option("sales", "Pricing, upgrades, new accounts")
								.build()));

		this.mock.server().verify();
	}

	@Test
	void sendsTheDocumentedScoreRequest() {
		expectBody("""
				{
					"state": "Help! My payouts have been failing for 3 days.",
					"model": "jev-latest",
					"questions": {
						"frustration": {
							"type": "score",
							"instructions": "How frustrated is the customer?",
							"criteria": ["Calm", "Frustrated", "Very angry"]
						}
					}
				}""");

		this.mock.client()
			.systemOne("Help! My payouts have been failing for 3 days.", Map.of("frustration",
					Score.of("How frustrated is the customer?", "Calm", "Frustrated", "Very angry")));

		this.mock.server().verify();
	}

	@Test
	void omitsCriteriaAndInstructionsWhenUnset() {
		expectBody("""
				{
					"state": "anything",
					"model": "jev-latest",
					"questions": { "probe": { "type": "noul", "instructions": "Is this a probe?" } }
				}""");

		this.mock.client().systemOne("anything", Map.of("probe", Noul.of("Is this a probe?")));

		this.mock.server().verify();
	}

	@Test
	void sendsAStructuredStateAndStructuredQuestion() {
		expectBody("""
				{
					"state": {
						"sender": { "display_name": "Beaver Dam Builders Ltd.", "email": "donotreply@payroll.example" },
						"message": "Reply with your login password so we can verify your identity."
					},
					"model": "jev-latest",
					"questions": {
						"requests_credentials": {
							"type": "noul",
							"instructions": {
								"question": "Does the `message` ask the recipient to disclose a sensitive credential?",
								"inspect": "message",
								"focus": "Look for a request to send the credential itself."
							},
							"criteria": {
								"true": {
									"what": "Asks the recipient to reply with a password, PIN or one-time code",
									"examples": ["Reply with your password", "Send us the 6-digit code"]
								},
								"false": { "what": "No sensitive credential is requested" }
							}
						}
					}
				}""");

		Map<String, Object> state = new java.util.LinkedHashMap<>();
		state.put("sender", Map.of("display_name", "Beaver Dam Builders Ltd.", "email", "donotreply@payroll.example"));
		state.put("message", "Reply with your login password so we can verify your identity.");

		Map<String, Object> instructions = new java.util.LinkedHashMap<>();
		instructions.put("question", "Does the `message` ask the recipient to disclose a sensitive credential?");
		instructions.put("inspect", "message");
		instructions.put("focus", "Look for a request to send the credential itself.");

		Map<String, Object> whenTrue = new java.util.LinkedHashMap<>();
		whenTrue.put("what", "Asks the recipient to reply with a password, PIN or one-time code");
		whenTrue.put("examples", List.of("Reply with your password", "Send us the 6-digit code"));

		this.mock.client()
			.systemOne(state,
					Map.of("requests_credentials",
							Noul.builder()
								.instructions(instructions)
								.whenTrue(whenTrue)
								.whenFalse(Map.of("what", "No sensitive credential is requested"))
								.build()));

		this.mock.server().verify();
	}

	@Test
	void sendsAnArrayStateForAThread() {
		expectBody("""
				{
					"state": ["Hi", "My customer number is TS1337.", "My card was charged twice."],
					"model": "jev-latest",
					"questions": { "probe": { "type": "noul", "instructions": "Is this a probe?" } }
				}""");

		this.mock.client()
			.systemOne(List.of("Hi", "My customer number is TS1337.", "My card was charged twice."),
					Map.of("probe", Noul.of("Is this a probe?")));

		this.mock.server().verify();
	}

	@Test
	void appliesTheClientDefaultModelWhenTheRequestNamesNone() {
		expectBody("""
				{
					"state": "anything",
					"model": "jev-1.13.0",
					"questions": { "probe": { "type": "noul", "instructions": "Is this a probe?" } }
				}""");

		this.mock.clientBuilder()
			.defaultModel("jev-1.13.0")
			.build()
			.systemOne("anything", Map.of("probe", Noul.of("Is this a probe?")));

		this.mock.server().verify();
	}

	@Test
	void keepsAnExplicitModelOnTheRequest() {
		expectBody("""
				{
					"state": "anything",
					"model": "jev-preview",
					"questions": { "probe": { "type": "noul", "instructions": "Is this a probe?" } }
				}""");

		SystemOneRequest request = SystemOneRequest.builder()
			.state(JsonContent.of("anything"))
			.model(SystemOneModel.PREVIEW.id())
			.question("probe", Noul.of("Is this a probe?"))
			.build();

		this.mock.client().systemOne(request);

		this.mock.server().verify();
	}

	@Test
	void listsModels() {
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.MODELS_URL))
			.andExpect(method(org.springframework.http.HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
			.andRespond(MockSystemOneServer.jsonResponse(
					"""
							{"models":[{"name":"jev-latest","description":"The latest iteration of SystemOne's System One Model: Jev","release_date":"2026-09-10T18:38:01.391457+00:00"},
													{"name":"jev-preview","description":"A preview version of `jev-latest`: should be better in most ways","release_date":"2026-09-10T18:39:06.057655+00:00"}]}"""));

		assertThat(this.mock.client().listModels()).extracting("name").containsExactly("jev-latest", "jev-preview");

		this.mock.server().verify();
	}

	@Test
	void fillsInTheClientDefaultModelWhenTheRequestNamesNone() {
		// The request-object overload used to demand a model at build time, which made
		// the client's default unreachable through it.
		expectBody("""
				{
					"state": "anything",
					"model": "jev-latest",
					"questions": { "probe": { "type": "noul", "instructions": "Is this a probe?" } }
				}""");

		SystemOneRequest request = SystemOneRequest.builder()
			.state("anything")
			.question("probe", Noul.of("Is this a probe?"))
			.build();
		assertThat(request.model()).isNull();

		this.mock.client().systemOne(request);

		this.mock.server().verify();
	}

	@Test
	void sendsTheSdkOwnedHeadersExactlyOnceAndKeepsCallerHeaders() {
		// A caller-supplied Authorization, Accept or Content-Type used to ride alongside
		// the client's value; servers reject a doubled Authorization outright.
		HttpHeaders callerHeaders = new HttpHeaders();
		callerHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer not-the-real-key");
		callerHeaders.set(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE);
		callerHeaders.set("X-Tenant", "acme");

		this.mock.server().expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL)).andExpect(request -> {
			HttpHeaders sent = request.getHeaders();
			assertThat(sent.get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer test-api-key");
			assertThat(sent.get(HttpHeaders.ACCEPT)).containsExactly(MediaType.APPLICATION_JSON_VALUE);
			assertThat(sent.get(HttpHeaders.CONTENT_TYPE)).containsExactly(MediaType.APPLICATION_JSON_VALUE);
			assertThat(sent.get("X-Tenant")).containsExactly("acme");
		}).andRespond(MockSystemOneServer.jsonResponse(OK_BODY));

		this.mock.clientBuilder()
			.headers(callerHeaders)
			.build()
			.systemOne("anything", Map.of("probe", Noul.of("Is this a probe?")));

		this.mock.server().verify();
	}

	@Test
	void accumulatesHeadersAcrossBuilderCallsAndCopiesThem() {
		// The Javadoc says "adds"; a second call used to replace the first set wholesale.
		HttpHeaders first = new HttpHeaders();
		first.set("X-Trace", "t-1");
		HttpHeaders second = new HttpHeaders();
		second.set("X-Tenant", "acme");

		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andExpect(header("X-Trace", "t-1"))
			.andExpect(header("X-Tenant", "acme"))
			.andExpect(headerDoesNotExist("X-Late"))
			.andRespond(MockSystemOneServer.jsonResponse(OK_BODY));

		SystemOneClient client = this.mock.clientBuilder().headers(first).headers(second).build();
		// The builder copies, so the caller's instance is not the client's.
		second.set("X-Late", "too-late");

		client.systemOne("anything", Map.of("probe", Noul.of("Is this a probe?")));

		this.mock.server().verify();
	}

	private void expectBody(String expectedJson) {
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andExpect(method(org.springframework.http.HttpMethod.POST))
			.andExpect(content().json(expectedJson, JsonCompareMode.STRICT))
			.andRespond(MockSystemOneServer.jsonResponse(OK_BODY));
	}

}
