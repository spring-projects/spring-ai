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

package org.springframework.ai.anthropic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Model;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for the Anthropic
 * <a href="https://platform.claude.com/docs/en/api/messages/batches">Message Batches
 * API</a> support: full submit → poll → read lifecycle, cancellation, and error handling
 * against the real API.
 *
 * <p>
 * <b>Gating.</b> Two switches guard this test, because a batch consumes tokens and its
 * completion time is not bounded by the API contract:
 * <ul>
 * <li>{@code ANTHROPIC_API_KEY} must be set — the repository-wide convention for provider
 * integration tests.</li>
 * <li>{@code ANTHROPIC_BATCH_IT_DISABLED=true} turns it off even when a key is present,
 * for runs that must not spend batch quota.</li>
 * </ul>
 * Anthropic ITs are also excluded from the {@code ci-fast-integration-tests} profile, so
 * this only runs under {@code -Pintegration-tests}.
 *
 * <p>
 * <b>Expect minutes, not seconds.</b> Batches are asynchronous by design and the API
 * allows up to 24 hours, though a small batch normally ends within a few minutes of
 * queueing. {@link #submitPollAndReadResults()} is the only test that waits, and it logs
 * every poll with the status and counters so a slow queue is visibly a slow queue rather
 * than a hang. Past {@link #COMPLETION_TIMEOUT} — 5 minutes by default, override with
 * {@code ANTHROPIC_BATCH_IT_TIMEOUT_MINUTES} — it {@link Assumptions#abort aborts}
 * instead of failing, because a slow queue on Anthropic's side is not a Spring AI
 * regression.
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 */
@SpringBootTest(classes = AnthropicBatchIT.Config.class)
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@DisabledIfEnvironmentVariable(named = "ANTHROPIC_BATCH_IT_DISABLED", matches = "(?i)true")
class AnthropicBatchIT {

	private static final Log logger = LogFactory.getLog(AnthropicBatchIT.class);

	private static final Duration COMPLETION_TIMEOUT = completionTimeout();

	private static final Duration POLL_INTERVAL = Duration.ofSeconds(20);

	private static final Duration RESULTS_TIMEOUT = Duration.ofMinutes(2);

	@Autowired
	private AnthropicBatchModel batchModel;

	private final List<String> createdBatchIds = new ArrayList<>();

	private static Duration completionTimeout() {
		String minutes = System.getenv("ANTHROPIC_BATCH_IT_TIMEOUT_MINUTES");
		return StringUtils.hasText(minutes) ? Duration.ofMinutes(Long.parseLong(minutes.trim()))
				: Duration.ofMinutes(5);
	}

	@AfterEach
	void deleteCreatedBatches() {
		for (String batchId : this.createdBatchIds) {
			cancelQuietly(batchId);
			deleteQuietly(batchId);
		}
		this.createdBatchIds.clear();
	}

	@Test
	void submitPollAndReadResults() {
		AnthropicBatch submitted = submit(List.of(
				AnthropicBatchRequest.of("spring-ai-batch-it-1", "Reply with exactly one word: ONE"),
				AnthropicBatchRequest.of("spring-ai-batch-it-2",
						new Prompt(List.of(new SystemMessage("Answer with a single uppercase word and nothing else."),
								new UserMessage("Reply with exactly one word: TWO")))),
				AnthropicBatchRequest.of("spring-ai-batch-it-3", "Reply with exactly one word: THREE",
						AnthropicChatOptions.builder().model(Model.CLAUDE_HAIKU_4_5).maxTokens(64).build())));

		assertThat(submitted.id()).isNotBlank();
		assertThat(submitted.status()).isIn(AnthropicBatchStatus.IN_PROGRESS, AnthropicBatchStatus.ENDED);
		assertThat(submitted.expiresAt()).isAfter(submitted.createdAt());

		AnthropicBatch ended = awaitEnded(submitted.id());
		assertThat(ended.requestCounts().total()).isEqualTo(3);
		assertThat(ended.requestCounts().processing()).isZero();
		assertThat(ended.resultsUrl()).isNotBlank();
		assertThat(ended.endedAt()).isNotNull();

		Map<String, AnthropicBatchResult> byCustomId = this.batchModel.results(submitted.id())
			.collectMap(AnthropicBatchResult::customId)
			.block(RESULTS_TIMEOUT);

		// Correlation is by customId only: the API does not preserve submission order.
		assertThat(byCustomId).containsOnlyKeys("spring-ai-batch-it-1", "spring-ai-batch-it-2", "spring-ai-batch-it-3");

		byCustomId.forEach((customId, result) -> {
			assertThat(result.status()).as("%s outcome, error was %s", customId, result.error())
				.isEqualTo(AnthropicBatchResultStatus.SUCCEEDED);
			assertThat(result.error()).isNull();
			assertThat(result.getText()).as("%s response text", customId).isNotBlank();
			// A batched message must carry the same metadata and usage as a realtime
			// call.
			assertThat(result.chatResponse()).isNotNull();
			assertThat(result.chatResponse().getMetadata().getId()).isNotBlank();
			assertThat(result.chatResponse().getMetadata().getModel()).isNotBlank();
			assertThat(result.chatResponse().getResult().getMetadata().getFinishReason()).isNotBlank();
			assertThat(result.usage()).isNotNull();
			assertThat(result.usage().getPromptTokens()).isPositive();
			assertThat(result.usage().getCompletionTokens()).isPositive();
			assertThat(result.usage().getTotalTokens()).isPositive();
		});

		// Same batch, fresh subscription: taking one element must not drain the whole
		// JSONL stream. Asserted here so the suite waits for a batch only once.
		AnthropicBatchResult first = this.batchModel.results(submitted.id()).next().block(RESULTS_TIMEOUT);
		assertThat(first).isNotNull();
		assertThat(first.customId()).startsWith("spring-ai-batch-it-");
	}

	@Test
	void cancelIsAcknowledged() {
		AnthropicBatch submitted = submit(List.of(AnthropicBatchRequest.of("spring-ai-batch-it-cancel",
				"Write a detailed multi-paragraph essay about the history of gardening.")));

		AnthropicBatch canceling = this.batchModel.cancel(submitted.id());

		assertThat(canceling.id()).isEqualTo(submitted.id());
		// A tiny batch can finish before the cancellation request lands.
		assertThat(canceling.status()).isIn(AnthropicBatchStatus.CANCELING, AnthropicBatchStatus.ENDED);
		if (canceling.isCanceling()) {
			assertThat(canceling.cancelInitiatedAt()).isNotNull();
		}
	}

	@Test
	void retrievingAnUnknownBatchFailsWithAClientError() {
		assertThatExceptionOfType(AnthropicServiceException.class)
			.isThrownBy(() -> this.batchModel.retrieve("msgbatch_01SpringAiNoSuchBatch00"))
			.satisfies(ex -> assertThat(ex.statusCode()).isBetween(400, 499));
	}

	private AnthropicBatch submit(List<AnthropicBatchRequest> requests) {
		AnthropicBatch batch = this.batchModel.submit(requests);
		this.createdBatchIds.add(batch.id());
		return batch;
	}

	/**
	 * Polls until the batch ends, logging every attempt.
	 * <p>
	 * The logging is not decoration: a batch is asynchronous by design, so a silent wait
	 * is indistinguishable from a hung test. Each line reports the elapsed time, the
	 * processing status and the per-outcome counters, so a slow queue is visibly a slow
	 * queue.
	 */
	private AnthropicBatch awaitEnded(String batchId) {
		long startedAt = System.nanoTime();
		logger.info("Waiting up to %s for batch %s to end (polling every %s)".formatted(COMPLETION_TIMEOUT, batchId,
				POLL_INTERVAL));
		try {
			Awaitility.await()
				.atMost(COMPLETION_TIMEOUT)
				.pollInterval(POLL_INTERVAL)
				.pollDelay(Duration.ofSeconds(2))
				.until(() -> {
					AnthropicBatch current = this.batchModel.retrieve(batchId);
					logger.info("  [%3ds] batch %s status=%s counts=%s".formatted(
							Duration.ofNanos(System.nanoTime() - startedAt).toSeconds(), batchId,
							current.status().getValue(), current.requestCounts()));
					return current.isEnded();
				});
		}
		catch (ConditionTimeoutException ex) {
			Assumptions.abort(
					"Batch %s had not ended after %s. The API allows up to 24 hours, so a slow queue on Anthropic's side is not a Spring AI failure; raise ANTHROPIC_BATCH_IT_TIMEOUT_MINUTES to wait longer."
						.formatted(batchId, COMPLETION_TIMEOUT));
		}
		AnthropicBatch ended = this.batchModel.retrieve(batchId);
		assertThat(ended.isEnded()).isTrue();
		return ended;
	}

	private void cancelQuietly(String batchId) {
		try {
			this.batchModel.cancel(batchId);
		}
		catch (RuntimeException ex) {
			// Best-effort cleanup: an already-ended batch cannot be canceled.
		}
	}

	private void deleteQuietly(String batchId) {
		try {
			this.batchModel.delete(batchId);
		}
		catch (RuntimeException ex) {
			// Best-effort cleanup: a batch that has not ended yet cannot be deleted.
		}
	}

	@SpringBootConfiguration
	public static class Config {

		@Bean
		public AnthropicClient anthropicClient() {
			String apiKey = System.getenv("ANTHROPIC_API_KEY");
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException(
						"You must provide an API key. Put it in an environment variable under the name ANTHROPIC_API_KEY");
			}
			return AnthropicSetup.setupSyncClient(null, apiKey, null, null, null, null);
		}

		@Bean
		public AnthropicBatchModel anthropicBatchModel(AnthropicClient client) {
			return AnthropicBatchModel.builder()
				.anthropicClient(client)
				.options(AnthropicChatOptions.builder().model(Model.CLAUDE_HAIKU_4_5).maxTokens(256).build())
				.build();
		}

	}

}
