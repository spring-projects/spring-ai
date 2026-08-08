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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.ErrorObject;
import com.anthropic.models.ErrorResponse;
import com.anthropic.models.InvalidRequestError;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.DeletedMessageBatch;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchErroredResult;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchRequestCounts;
import com.anthropic.models.messages.batches.MessageBatchResult;
import com.anthropic.models.messages.batches.MessageBatchSucceededResult;
import com.anthropic.services.blocking.MessageService;
import com.anthropic.services.blocking.messages.BatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DefaultAnthropicBatchModel}. Exercises SDK parameter
 * construction, batch and result mapping, out-of-order results, per-request errors and
 * stream cleanup with a mocked SDK client — no API key and no network access required.
 *
 * @author Ricken Bazolo
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnthropicBatchModelTests {

	@Mock
	private AnthropicClient anthropicClient;

	@Mock
	private MessageService messageService;

	@Mock
	private BatchService batchService;

	private AnthropicBatchModel batchModel;

	@BeforeEach
	void setUp() {
		given(this.anthropicClient.messages()).willReturn(this.messageService);
		given(this.messageService.batches()).willReturn(this.batchService);

		this.batchModel = AnthropicBatchModel.builder()
			.anthropicClient(this.anthropicClient)
			.options(AnthropicChatOptions.builder().model("claude-haiku-4-5").maxTokens(256).build())
			.build();
	}

	@Test
	void submitMapsEachPromptThroughTheRealtimeRequestConversion() {
		MessageBatch accepted = mockMessageBatch("msgbatch_1", MessageBatch.ProcessingStatus.IN_PROGRESS);
		given(this.batchService.create(any(BatchCreateParams.class), any(RequestOptions.class))).willReturn(accepted);

		AnthropicBatch batch = this.batchModel.submit(List.of(
				AnthropicBatchRequest.of("req-1",
						new Prompt(List.of(new SystemMessage("Be brief."), new UserMessage("Hello")))),
				AnthropicBatchRequest.of("req-2", "World")));

		ArgumentCaptor<BatchCreateParams> captor = ArgumentCaptor.forClass(BatchCreateParams.class);
		verify(this.batchService).create(captor.capture(), any(RequestOptions.class));

		List<BatchCreateParams.Request> requests = captor.getValue().requests();
		assertThat(requests).hasSize(2);
		assertThat(requests.stream().map(BatchCreateParams.Request::customId)).containsExactly("req-1", "req-2");

		BatchCreateParams.Request.Params first = requests.get(0).params();
		assertThat(first.model().asString()).isEqualTo("claude-haiku-4-5");
		assertThat(first.maxTokens()).isEqualTo(256L);
		assertThat(first.system().orElseThrow().asString()).isEqualTo("Be brief.");
		assertThat(first.messages()).hasSize(1);
		// Batch entries cannot stream.
		assertThat(first._additionalProperties()).doesNotContainKey("stream");

		assertThat(batch.id()).isEqualTo("msgbatch_1");
		assertThat(batch.status()).isEqualTo(AnthropicBatchStatus.IN_PROGRESS);
		assertThat(batch.isEnded()).isFalse();
	}

	@Test
	void submitHonoursPerRequestOptions() {
		MessageBatch accepted = mockMessageBatch("msgbatch_2", MessageBatch.ProcessingStatus.IN_PROGRESS);
		given(this.batchService.create(any(BatchCreateParams.class), any(RequestOptions.class))).willReturn(accepted);

		this.batchModel.submit(List.of(AnthropicBatchRequest.of("req-1", "Hello",
				AnthropicChatOptions.builder().model("claude-opus-4-5").maxTokens(64).temperature(0.2).build())));

		ArgumentCaptor<BatchCreateParams> captor = ArgumentCaptor.forClass(BatchCreateParams.class);
		verify(this.batchService).create(captor.capture(), any(RequestOptions.class));

		BatchCreateParams.Request.Params params = captor.getValue().requests().get(0).params();
		assertThat(params.model().asString()).isEqualTo("claude-opus-4-5");
		assertThat(params.maxTokens()).isEqualTo(64L);
		assertThat(params.temperature()).contains(0.2);
	}

	@Test
	void submitRejectsAnEmptyBatch() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.batchModel.submit(List.of()))
			.withMessageContaining("requests must not be empty");
	}

	@Test
	void submitRejectsDuplicateCustomIds() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.batchModel
				.submit(List.of(AnthropicBatchRequest.of("same", "a"), AnthropicBatchRequest.of("same", "b"))))
			.withMessageContaining("Duplicate customId");
	}

	@Test
	void retrieveMapsStatusCountersAndTimestamps() {
		MessageBatch messageBatch = mockMessageBatch("msgbatch_3", MessageBatch.ProcessingStatus.ENDED);
		given(this.batchService.retrieve(eq("msgbatch_3"), any(RequestOptions.class))).willReturn(messageBatch);

		AnthropicBatch batch = this.batchModel.retrieve("msgbatch_3");

		assertThat(batch.status()).isEqualTo(AnthropicBatchStatus.ENDED);
		assertThat(batch.isEnded()).isTrue();
		assertThat(batch.requestCounts()).isEqualTo(new AnthropicBatchRequestCounts(1, 2, 1, 0, 0));
		assertThat(batch.requestCounts().total()).isEqualTo(4);
		assertThat(batch.requestCounts().completed()).isEqualTo(3);
		assertThat(batch.resultsUrl()).isEqualTo("https://api.anthropic.com/v1/messages/batches/msgbatch_3/results");
		assertThat(batch.endedAt()).isNotNull();
	}

	@Test
	void retrieveMapsUnknownStatusesWithoutFailing() {
		MessageBatch messageBatch = mockMessageBatch("msgbatch_x",
				MessageBatch.ProcessingStatus.of("brand_new_status"));
		given(this.batchService.retrieve(eq("msgbatch_x"), any(RequestOptions.class))).willReturn(messageBatch);

		assertThat(this.batchModel.retrieve("msgbatch_x").status()).isEqualTo(AnthropicBatchStatus.UNKNOWN);
	}

	@Test
	void resultsCorrelateByCustomIdEvenWhenReturnedOutOfOrder() {
		StreamResponse<MessageBatchIndividualResponse> streamResponse = mockResults(
				succeededResponse("req-2", "second"), succeededResponse("req-1", "first"));
		given(this.batchService.resultsStreaming(eq("msgbatch_4"), any(RequestOptions.class)))
			.willReturn(streamResponse);

		Map<String, AnthropicBatchResult> byCustomId = this.batchModel.results("msgbatch_4")
			.collectMap(AnthropicBatchResult::customId)
			.block();

		assertThat(byCustomId).containsOnlyKeys("req-1", "req-2");
		assertThat(byCustomId.get("req-1").getText()).isEqualTo("first");
		assertThat(byCustomId.get("req-2").getText()).isEqualTo("second");
		assertThat(byCustomId.get("req-1").isSucceeded()).isTrue();
		verify(streamResponse).close();
	}

	@Test
	void resultsConvertSucceededMessagesLikeARealtimeCall() {
		StreamResponse<MessageBatchIndividualResponse> streamResponse = mockResults(
				succeededResponse("req-1", "Hello there"));
		given(this.batchService.resultsStreaming(eq("msgbatch_5"), any(RequestOptions.class)))
			.willReturn(streamResponse);

		AnthropicBatchResult result = this.batchModel.results("msgbatch_5").blockFirst();

		assertThat(result).isNotNull();
		assertThat(result.status()).isEqualTo(AnthropicBatchResultStatus.SUCCEEDED);
		assertThat(result.error()).isNull();
		assertThat(result.chatResponse()).isNotNull();
		assertThat(result.chatResponse().getResult().getOutput().getText()).isEqualTo("Hello there");
		assertThat(result.chatResponse().getResult().getMetadata().getFinishReason())
			.isEqualTo(StopReason.END_TURN.toString());
		assertThat(result.chatResponse().getMetadata().getId()).isEqualTo("msg_batch_req-1");
		assertThat(result.usage()).isNotNull();
		assertThat(result.usage().getPromptTokens()).isEqualTo(10);
		assertThat(result.usage().getCompletionTokens()).isEqualTo(20);
		assertThat(result.usage().getTotalTokens()).isEqualTo(30);
	}

	@Test
	void resultsSurfaceIndividualErrorsWithoutHidingTheOtherEntries() {
		StreamResponse<MessageBatchIndividualResponse> streamResponse = mockResults(
				erroredResponse("req-1", "max_tokens must be positive"), succeededResponse("req-2", "fine"),
				terminalResponse("req-3", TerminalKind.CANCELED), terminalResponse("req-4", TerminalKind.EXPIRED));
		given(this.batchService.resultsStreaming(eq("msgbatch_6"), any(RequestOptions.class)))
			.willReturn(streamResponse);

		List<AnthropicBatchResult> results = this.batchModel.results("msgbatch_6").collectList().block();

		assertThat(results).hasSize(4);
		AnthropicBatchResult errored = results.get(0);
		assertThat(errored.status()).isEqualTo(AnthropicBatchResultStatus.ERRORED);
		assertThat(errored.chatResponse()).isNull();
		assertThat(errored.error()).isNotNull();
		assertThat(errored.error().type()).isEqualTo("invalid_request_error");
		assertThat(errored.error().message()).isEqualTo("max_tokens must be positive");
		assertThat(errored.error().requestId()).isEqualTo("req_abc");

		assertThat(results.get(1).status()).isEqualTo(AnthropicBatchResultStatus.SUCCEEDED);
		assertThat(results.get(2).status()).isEqualTo(AnthropicBatchResultStatus.CANCELED);
		assertThat(results.get(3).status()).isEqualTo(AnthropicBatchResultStatus.EXPIRED);
		assertThat(results.get(2).chatResponse()).isNull();
		assertThat(results.get(3).error()).isNull();
	}

	@Test
	void resultsCloseTheSdkStreamWhenTheSubscriberCancels() {
		StreamResponse<MessageBatchIndividualResponse> streamResponse = mockResults(succeededResponse("req-1", "a"),
				succeededResponse("req-2", "b"));
		given(this.batchService.resultsStreaming(eq("msgbatch_7"), any(RequestOptions.class)))
			.willReturn(streamResponse);

		AnthropicBatchResult first = this.batchModel.results("msgbatch_7").next().block();

		assertThat(first).isNotNull();
		verify(streamResponse).close();
	}

	@Test
	void resultsAreLazyAndDoNotCallTheApiUntilSubscribed() {
		this.batchModel.results("msgbatch_8");

		verify(this.batchService, never()).resultsStreaming(any(String.class), any(RequestOptions.class));
	}

	@Test
	void cancelDelegatesToTheProvider() {
		MessageBatch canceling = mockMessageBatch("msgbatch_9", MessageBatch.ProcessingStatus.CANCELING);
		given(this.batchService.cancel(eq("msgbatch_9"), any(RequestOptions.class))).willReturn(canceling);

		AnthropicBatch batch = this.batchModel.cancel("msgbatch_9");

		assertThat(batch.status()).isEqualTo(AnthropicBatchStatus.CANCELING);
		assertThat(batch.isCanceling()).isTrue();
		verify(this.batchService).cancel(eq("msgbatch_9"), any(RequestOptions.class));
	}

	@Test
	void deleteDelegatesToTheProvider() {
		DeletedMessageBatch deleted = mock(DeletedMessageBatch.class);
		given(this.batchService.delete(eq("msgbatch_10"), any(RequestOptions.class))).willReturn(deleted);

		this.batchModel.delete("msgbatch_10");

		verify(this.batchService).delete(eq("msgbatch_10"), any(RequestOptions.class));
	}

	@Test
	void controlOperationsRejectBlankBatchIds() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.batchModel.retrieve(" "));
		assertThatIllegalArgumentException().isThrownBy(() -> this.batchModel.cancel(""));
		assertThatIllegalArgumentException().isThrownBy(() -> this.batchModel.delete(""));
		assertThatIllegalArgumentException().isThrownBy(() -> this.batchModel.results(""));
	}

	// --- fixtures ---

	private static MessageBatch mockMessageBatch(String id, MessageBatch.ProcessingStatus status) {
		MessageBatchRequestCounts counts = mock(MessageBatchRequestCounts.class);
		given(counts.processing()).willReturn(1L);
		given(counts.succeeded()).willReturn(2L);
		given(counts.errored()).willReturn(1L);
		given(counts.canceled()).willReturn(0L);
		given(counts.expired()).willReturn(0L);

		OffsetDateTime now = OffsetDateTime.parse("2026-07-30T10:15:30Z");

		MessageBatch messageBatch = mock(MessageBatch.class);
		given(messageBatch.id()).willReturn(id);
		given(messageBatch.processingStatus()).willReturn(status);
		given(messageBatch.requestCounts()).willReturn(counts);
		given(messageBatch.createdAt()).willReturn(now);
		given(messageBatch.expiresAt()).willReturn(now.plusDays(1));
		given(messageBatch.endedAt()).willReturn(Optional.of(now.plusHours(2)));
		given(messageBatch.cancelInitiatedAt()).willReturn(Optional.empty());
		given(messageBatch.archivedAt()).willReturn(Optional.empty());
		given(messageBatch.resultsUrl())
			.willReturn(Optional.of("https://api.anthropic.com/v1/messages/batches/" + id + "/results"));
		return messageBatch;
	}

	@SuppressWarnings("unchecked")
	private static StreamResponse<MessageBatchIndividualResponse> mockResults(
			MessageBatchIndividualResponse... responses) {
		StreamResponse<MessageBatchIndividualResponse> streamResponse = mock(StreamResponse.class);
		given(streamResponse.stream()).willReturn(Stream.of(responses));
		return streamResponse;
	}

	private static MessageBatchIndividualResponse succeededResponse(String customId, String text) {
		Message message = mockMessage("msg_batch_" + customId, text);
		MessageBatchSucceededResult succeeded = mock(MessageBatchSucceededResult.class);
		given(succeeded.message()).willReturn(message);

		MessageBatchResult result = mock(MessageBatchResult.class);
		given(result.isSucceeded()).willReturn(true);
		given(result.asSucceeded()).willReturn(succeeded);

		return individualResponse(customId, result);
	}

	private static MessageBatchIndividualResponse erroredResponse(String customId, String message) {
		ErrorResponse errorResponse = ErrorResponse.builder()
			.error(ErrorObject.ofInvalidRequestError(InvalidRequestError.builder().message(message).build()))
			.requestId("req_abc")
			.build();
		MessageBatchErroredResult errored = mock(MessageBatchErroredResult.class);
		given(errored.error()).willReturn(errorResponse);

		MessageBatchResult result = mock(MessageBatchResult.class);
		given(result.isSucceeded()).willReturn(false);
		given(result.isErrored()).willReturn(true);
		given(result.asErrored()).willReturn(errored);

		return individualResponse(customId, result);
	}

	private enum TerminalKind {

		CANCELED, EXPIRED

	}

	private static MessageBatchIndividualResponse terminalResponse(String customId, TerminalKind kind) {
		MessageBatchResult result = mock(MessageBatchResult.class);
		given(result.isSucceeded()).willReturn(false);
		given(result.isErrored()).willReturn(false);
		given(result.isCanceled()).willReturn(kind == TerminalKind.CANCELED);
		given(result.isExpired()).willReturn(kind == TerminalKind.EXPIRED);

		return individualResponse(customId, result);
	}

	private static MessageBatchIndividualResponse individualResponse(String customId, MessageBatchResult result) {
		MessageBatchIndividualResponse response = mock(MessageBatchIndividualResponse.class);
		given(response.customId()).willReturn(customId);
		given(response.result()).willReturn(result);
		return response;
	}

	private static Message mockMessage(String id, String text) {
		TextBlock textBlock = mock(TextBlock.class);
		given(textBlock.text()).willReturn(text);
		given(textBlock.citations()).willReturn(Optional.empty());

		ContentBlock contentBlock = mock(ContentBlock.class);
		given(contentBlock.isText()).willReturn(true);
		given(contentBlock.asText()).willReturn(textBlock);

		Usage usage = mock(Usage.class);
		given(usage.inputTokens()).willReturn(10L);
		given(usage.outputTokens()).willReturn(20L);
		given(usage.cacheReadInputTokens()).willReturn(Optional.empty());
		given(usage.cacheCreationInputTokens()).willReturn(Optional.empty());

		Message message = mock(Message.class);
		given(message.id()).willReturn(id);
		given(message.model()).willReturn(Model.CLAUDE_HAIKU_4_5);
		given(message.content()).willReturn(List.of(contentBlock));
		given(message.stopReason()).willReturn(Optional.of(StopReason.END_TURN));
		given(message.usage()).willReturn(usage);
		return message;
	}

}
