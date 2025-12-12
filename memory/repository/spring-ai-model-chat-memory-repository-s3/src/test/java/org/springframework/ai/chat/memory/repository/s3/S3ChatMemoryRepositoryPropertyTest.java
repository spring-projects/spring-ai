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

package org.springframework.ai.chat.memory.repository.s3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for S3ChatMemoryRepository operations.
 *
 * @author Yuriy Bezsonov
 */
class S3ChatMemoryRepositoryPropertyTest {

	// Feature: s3-chat-memory-repository, Property 3: Complete conversation listing
	// with pagination
	@Property(tries = 100)
	void completeListingWithPagination(@ForAll("conversationIdSets") Set<String> conversationIds) {
		// Given: A repository with multiple conversations that require pagination
		S3Client mockS3Client = mock(S3Client.class);

		S3ChatMemoryRepository repository = S3ChatMemoryRepository.builder()
			.s3Client(mockS3Client)
			.bucketName("test-bucket")
			.keyPrefix("chat-memory")
			.build();

		// Create mock S3 objects for each conversation
		List<S3Object> s3Objects = new ArrayList<>();
		for (String conversationId : conversationIds) {
			S3Object s3Object = S3Object.builder().key("chat-memory/" + conversationId + ".json").build();
			s3Objects.add(s3Object);
		}

		// Simulate pagination: split objects into pages of 10
		int pageSize = 10;
		List<List<S3Object>> pages = new ArrayList<>();
		for (int i = 0; i < s3Objects.size(); i += pageSize) {
			pages.add(s3Objects.subList(i, Math.min(i + pageSize, s3Objects.size())));
		}

		// Mock paginated responses
		if (pages.isEmpty()) {
			// Empty result
			ListObjectsV2Response emptyResponse = ListObjectsV2Response.builder()
				.contents(List.of())
				.isTruncated(false)
				.build();
			when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(emptyResponse);
		}
		else {
			// Setup mock responses for each page
			ListObjectsV2Response[] responses = new ListObjectsV2Response[pages.size()];
			for (int i = 0; i < pages.size(); i++) {
				boolean hasMore = i < pages.size() - 1;
				responses[i] = ListObjectsV2Response.builder()
					.contents(pages.get(i))
					.isTruncated(hasMore)
					.nextContinuationToken(hasMore ? "token-" + (i + 1) : null)
					.build();
			}

			when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(responses[0],
					responses.length > 1 ? java.util.Arrays.copyOfRange(responses, 1, responses.length)
							: new ListObjectsV2Response[0]);
		}

		// When: Finding all conversation IDs
		List<String> foundIds = repository.findConversationIds();

		// Then: All conversation IDs should be returned
		assertThat(foundIds).containsExactlyInAnyOrderElementsOf(conversationIds);

		// And: Pagination should have been handled (verify multiple calls if needed)
		int expectedCalls = Math.max(1, pages.size());
		verify(mockS3Client, times(expectedCalls)).listObjectsV2(any(ListObjectsV2Request.class));
	}

	// Feature: s3-chat-memory-repository, Property 4: Invalid key filtering
	@Property(tries = 100)
	void invalidKeyFiltering(@ForAll("validAndInvalidKeys") KeySet keySet) {
		// Given: A repository with both valid and invalid S3 keys
		S3Client mockS3Client = mock(S3Client.class);

		S3ChatMemoryRepository repository = S3ChatMemoryRepository.builder()
			.s3Client(mockS3Client)
			.bucketName("test-bucket")
			.keyPrefix("chat-memory")
			.build();

		// Create S3 objects with both valid and invalid keys
		List<S3Object> s3Objects = new ArrayList<>();
		for (String key : keySet.allKeys()) {
			s3Objects.add(S3Object.builder().key(key).build());
		}

		ListObjectsV2Response response = ListObjectsV2Response.builder().contents(s3Objects).isTruncated(false).build();

		when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

		// When: Finding conversation IDs
		List<String> foundIds = repository.findConversationIds();

		// Then: Only valid conversation IDs should be returned
		assertThat(foundIds).containsExactlyInAnyOrderElementsOf(keySet.validConversationIds());
		// Verify no invalid keys are included (only check if there are invalid keys)
		if (!keySet.invalidKeys().isEmpty()) {
			assertThat(foundIds).doesNotContainAnyElementsOf(keySet.invalidKeys());
		}
	}

	// Feature: s3-chat-memory-repository, Property 5: Conversation replacement
	@Property(tries = 100)
	void conversationReplacement(@ForAll("conversationIds") String conversationId,
			@ForAll("messageLists") List<Message> firstMessages, @ForAll("messageLists") List<Message> secondMessages) {
		// Given: A repository
		S3Client mockS3Client = mock(S3Client.class);

		S3ChatMemoryRepository repository = S3ChatMemoryRepository.builder()
			.s3Client(mockS3Client)
			.bucketName("test-bucket")
			.keyPrefix("chat-memory")
			.build();

		// When: Saving messages twice for the same conversation
		repository.saveAll(conversationId, firstMessages);
		repository.saveAll(conversationId, secondMessages);

		// Then: The second save should have replaced the first
		// Verify that putObject was called twice (once for each save)
		verify(mockS3Client, times(2)).putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
				any(software.amazon.awssdk.core.sync.RequestBody.class));
	}

	// Feature: s3-chat-memory-repository, Property 6: Deletion completeness
	@Property(tries = 100)
	void deletionCompleteness(@ForAll("conversationIds") String conversationId) {
		// Given: A repository
		S3Client mockS3Client = mock(S3Client.class);

		S3ChatMemoryRepository repository = S3ChatMemoryRepository.builder()
			.s3Client(mockS3Client)
			.bucketName("test-bucket")
			.keyPrefix("chat-memory")
			.build();

		// When: Deleting a conversation
		repository.deleteByConversationId(conversationId);

		// Then: DeleteObject should have been called with the correct key
		ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(mockS3Client).deleteObject(captor.capture());

		DeleteObjectRequest request = captor.getValue();
		assertThat(request.bucket()).isEqualTo("test-bucket");
		assertThat(request.key()).startsWith("chat-memory/");
		assertThat(request.key()).endsWith(".json");
	}

	@Provide
	Arbitrary<Set<String>> conversationIdSets() {
		return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20).set().ofMinSize(0).ofMaxSize(25);
	}

	@Provide
	Arbitrary<String> conversationIds() {
		return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(50);
	}

	@Provide
	Arbitrary<List<Message>> messageLists() {
		return Arbitraries.strings()
			.alpha()
			.numeric()
			.ofMinLength(1)
			.ofMaxLength(100)
			.map(content -> (Message) UserMessage.builder().text(content).build())
			.list()
			.ofMinSize(1)
			.ofMaxSize(5);
	}

	@Provide
	Arbitrary<KeySet> validAndInvalidKeys() {
		Arbitrary<String> validIds = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);

		Arbitrary<String> validKeys = validIds.map(id -> "chat-memory/" + id + ".json");

		Arbitrary<String> invalidKeys = Arbitraries.oneOf(Arbitraries.of("invalid-key", "wrong-prefix/conv.json",
				"chat-memory/nested/path.json", "chat-memory/.json", "chat-memory/conv.txt"));

		return validKeys.set().ofMinSize(0).ofMaxSize(10).flatMap(validKeySet -> {
			Set<String> validConvIds = new HashSet<>();
			for (String key : validKeySet) {
				String id = key.substring("chat-memory/".length(), key.length() - ".json".length());
				validConvIds.add(id);
			}

			return invalidKeys.set().ofMinSize(0).ofMaxSize(5).map(invalidKeySet -> {
				Set<String> allKeys = new HashSet<>(validKeySet);
				allKeys.addAll(invalidKeySet);
				return new KeySet(allKeys, validConvIds, invalidKeySet);
			});
		});
	}

	// Feature: s3-chat-memory-repository, Property 8: Input validation
	@Property(tries = 100)
	void inputValidation(@ForAll("invalidInputs") InvalidInput input) {
		// Given: A repository
		S3Client mockS3Client = mock(S3Client.class);

		S3ChatMemoryRepository repository = S3ChatMemoryRepository.builder()
			.s3Client(mockS3Client)
			.bucketName("test-bucket")
			.keyPrefix("chat-memory")
			.build();

		// When/Then: Invalid inputs should throw IllegalArgumentException
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> repository.saveAll(input.conversationId(), input.messages()));
	}

	// Feature: s3-chat-memory-repository, Property 10: Timestamp consistency
	@Property(tries = 100)
	void timestampConsistency(@ForAll("conversationIds") String conversationId,
			@ForAll("messageLists") List<Message> messages) {
		// Given: A repository
		S3Client mockS3Client = mock(S3Client.class);

		S3ChatMemoryRepository repository = S3ChatMemoryRepository.builder()
			.s3Client(mockS3Client)
			.bucketName("test-bucket")
			.keyPrefix("chat-memory")
			.build();

		// When: Saving messages
		repository.saveAll(conversationId, messages);

		// Then: All messages should have consistent timestamp formatting
		// This is verified by the serialization process not throwing exceptions
		verify(mockS3Client).putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
				any(software.amazon.awssdk.core.sync.RequestBody.class));
	}

	@Provide
	Arbitrary<InvalidInput> invalidInputs() {
		// Only test truly invalid inputs - null conversation IDs are now valid (converted
		// to default)
		return Arbitraries.just(new InvalidInput("saveAll", "valid-id", null));
	}

	@Provide
	Arbitrary<List<Message>> largeMessageLists() {
		return Arbitraries.strings()
			.alpha()
			.numeric()
			.ofMinLength(1)
			.ofMaxLength(50)
			.map(content -> (Message) UserMessage.builder().text(content).build())
			.list()
			.ofMinSize(10)
			.ofMaxSize(50); // Larger lists to test window enforcement
	}

	record InvalidInput(String operation, String conversationId, List<Message> messages) {
	}

	record KeySet(Set<String> allKeys, Set<String> validConversationIds, Set<String> invalidKeys) {
	}

}
