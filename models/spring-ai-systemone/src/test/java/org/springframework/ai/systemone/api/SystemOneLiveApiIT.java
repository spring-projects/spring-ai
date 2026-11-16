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
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.systemone.api.exception.SystemOneAuthenticationException;
import org.springframework.ai.systemone.api.exception.SystemOneUnprocessableEntityException;
import org.springframework.ai.systemone.api.question.Choice;
import org.springframework.ai.systemone.api.question.Noul;
import org.springframework.ai.systemone.api.question.Score;
import org.springframework.ai.systemone.api.question.SystemOneRequest;
import org.springframework.ai.systemone.api.response.ModelMetadata;
import org.springframework.ai.systemone.api.response.SystemOneResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The client against the hosted System One service.
 *
 * <p>
 * Skipped unless {@code SYSTEMONE_API_KEY} is set, which keeps {@code mvn verify} offline
 * by default, and skipped when {@code SYSTEMONE_BASE_URL} is set, since the key is then
 * meant for another server and must not be sent to the hosted one. The rest of the suite
 * pins its request and response bodies against fixtures; this is the only place that
 * checks those fixtures still describe the live service.
 *
 * <p>
 * The server runs a model, so nothing here asserts an exact number. The assertions are on
 * shape, range and plumbing — the things that break when the API changes underneath the
 * SDK. States are kept to a sentence: input is billed per token and a whole run costs a
 * fraction of a cent.
 *
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "SYSTEMONE_API_KEY", matches = ".+",
		disabledReason = "Set SYSTEMONE_API_KEY to run the client against the hosted service")
@DisabledIfEnvironmentVariable(named = "SYSTEMONE_BASE_URL", matches = ".+",
		disabledReason = "SYSTEMONE_BASE_URL points at another server, and these tests are about the hosted service")
class SystemOneLiveApiIT {

	private final SystemOneClient client = SystemOneClient.builder()
		.apiKey(System.getenv("SYSTEMONE_API_KEY"))
		.defaultModel(SystemOneModel.LATEST.id())
		.build();

	@Test
	void listsTheModelsTheAccountCanUse() {
		List<ModelMetadata> models = this.client.listModels();

		assertThat(models).isNotEmpty();
		assertThat(models).extracting(ModelMetadata::name).contains(SystemOneModel.LATEST.id());
		assertThat(models).allSatisfy(model -> {
			assertThat(model.name()).isNotBlank();
			assertThat(model.description()).isNotBlank();
			// An ISO-8601 timestamp, not the yyyy-MM-dd the client originally assumed.
			assertThat(model.releaseDate()).isNotBlank();
		});
	}

	@Test
	void answersAllThreePrimitivesInOneCall() {
		SystemOneResponse response = this.client.systemOne("Help! My payouts have been failing for 3 days.",
				Map.of("is_urgent", Noul.of("Does this convey urgency?"), "department",
						Choice.builder()
							.instructions("Which team should handle this?")
							.option("billing", "Payments, invoicing, refunds")
							.option("technical", "Bugs, outages, integrations")
							.option("sales", "Pricing, upgrades, new accounts")
							.build(),
						"frustration",
						Score.of("How frustrated is the customer?", "Calm", "Frustrated", "Very angry")));

		assertThat(response.noulValue("is_urgent")).isBetween(0.0, 1.0);

		assertThat(response.choiceValue("department")).isIn("billing", "technical", "sales");
		assertThat(response.choice("department").confidence()).isBetween(0.0, 1.0);
		assertThat(response.choice("department").probabilities()).containsOnlyKeys("billing", "technical", "sales");

		assertThat(response.scoreValue("frustration")).isBetween(0.0, 2.0);
		assertThat(response.score("frustration").labelOf(0).asText()).isEqualTo("Calm");
		assertThat(response.score("frustration").labelOf(2).asText()).isEqualTo("Very angry");

		assertThat(response.requestId()).isNotBlank();
	}

	@Test
	void reportsTheResolvedModelRatherThanTheAliasSent() {
		SystemOneResponse response = this.client.systemOne("A short sentence.",
				Map.of("probe", Noul.of("Is this a sentence?")));

		// jev-latest is an alias; the server answers with the version it resolved to, so
		// the id travels with the answer it produced.
		assertThat(response.model()).isNotBlank().isNotEqualTo(SystemOneModel.LATEST.id());
	}

	@Test
	void reportsTokenUsage() {
		SystemOneResponse response = this.client.systemOne("A short sentence.",
				Map.of("probe", Noul.of("Is this a sentence?")));

		assertThat(response.usage().inputTokens()).isNotNull().isPositive();
		assertThat(response.usage().outputTokens()).isNotNull().isPositive();
		assertThat(response.usage().totalTokens()).isPositive();
	}

	@Test
	void mapsARejectedKeyOntoAnAuthenticationException() {
		SystemOneClient badKey = SystemOneClient.builder()
			.apiKey("sk-not-a-real-key")
			.defaultModel(SystemOneModel.LATEST.id())
			.retryTemplate(SystemOneRetryUtils.noRetryTemplate())
			.build();

		assertThatExceptionOfType(SystemOneAuthenticationException.class)
			.isThrownBy(() -> badKey.systemOne("anything", Map.of("probe", Noul.of("Is this a probe?"))))
			.satisfies(ex -> {
				assertThat(ex.status()).isEqualTo(401);
				assertThat(ex.errorType()).isEqualTo("authentication_error");
				assertThat(ex.errorMessage()).isNotBlank();
			});
	}

	@Test
	void acceptsAnObjectStateAndStructuredInstructions() {
		// The documented recipe for a structured question: a JSON object as state, and
		// instructions that name which field to inspect.
		SystemOneResponse response = this.client.systemOne(
				Map.of("message", "Please reply with your 6-digit login code so we can verify you."),
				Map.of("requests_credentials", Noul.builder()
					.instructions(Map.of("question", "Does the `message` ask the recipient to disclose a credential?",
							"inspect", "message"))
					.whenTrue(Map.of("what", "Asks the recipient to reply with a password, PIN or one-time code"))
					.whenFalse("No sensitive credential is requested")
					.build()));

		// The one case the structure exists to get right, so it is safe to assert on.
		assertThat(response.noulValue("requests_credentials")).isGreaterThan(0.5d);
	}

	@Test
	void acceptsAnArrayStateForAThread() {
		SystemOneResponse response = this.client.systemOne(
				List.of("Hi", "My card was charged twice.", "Please refund one of them."),
				Map.of("mentions_billing", Noul.of("Does the conversation concern a payment or a charge?")));

		assertThat(response.noulValue("mentions_billing")).isGreaterThan(0.5d);
	}

	@Test
	void answersOnThePreviewModelToo() {
		SystemOneResponse response = this.client.systemOne(SystemOneRequest.builder()
			.state("A short sentence.")
			.model(SystemOneModel.PREVIEW.id())
			.question("probe", Noul.of("Is this a sentence?"))
			.build());

		assertThat(response.noulValue("probe")).isBetween(0.0d, 1.0d);
		assertThat(response.model()).isNotBlank();
	}

	@Test
	void mapsAMalformedRequestOntoValidationErrors() {
		// An empty state is rejected by request validation rather than by the model.
		SystemOneRequest request = new SystemOneRequest(JsonContent.NULL, SystemOneModel.LATEST.id(),
				Map.of("probe", Noul.of("Is this a probe?")));

		assertThatExceptionOfType(SystemOneUnprocessableEntityException.class)
			.isThrownBy(() -> this.client.systemOne(request))
			.satisfies(ex -> {
				assertThat(ex.status()).isEqualTo(422);
				assertThat(ex.validationErrors()).isNotEmpty();
				assertThat(ex.validationErrors()).allSatisfy(error -> assertThat(error.msg()).isNotBlank());
			});
	}

}
