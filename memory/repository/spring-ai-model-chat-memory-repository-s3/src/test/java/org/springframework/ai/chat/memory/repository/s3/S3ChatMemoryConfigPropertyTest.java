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

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Property-based tests for S3ChatMemoryConfig.
 *
 * @author Yuriy Bezsonov
 */
class S3ChatMemoryConfigPropertyTest {

	// Feature: s3-chat-memory-repository, Property 15: Configuration property binding
	@Property(tries = 100)
	void configurationPropertyBinding(@ForAll("validConfigParams") ConfigParams params) {
		// Given: A repository with random valid configuration
		S3Client mockS3Client = mock(S3Client.class);

		S3ChatMemoryRepository repository = S3ChatMemoryRepository.builder()
			.s3Client(mockS3Client)
			.bucketName(params.bucketName())
			.keyPrefix(params.keyPrefix())
			.build();

		// When: Saving messages
		List<Message> messages = List.of(UserMessage.builder().text("test message").build());
		repository.saveAll("test-conversation", messages);

		// Then: S3 operations should use the configured bucket name
		ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(mockS3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

		PutObjectRequest capturedRequest = requestCaptor.getValue();
		assertThat(capturedRequest.bucket()).isEqualTo(params.bucketName());

		// And: The key should use the configured prefix
		String expectedPrefix = params.keyPrefix();
		assertThat(capturedRequest.key()).startsWith(expectedPrefix);
	}

	@Provide
	Arbitrary<ConfigParams> validConfigParams() {
		Arbitrary<String> bucketNames = Arbitraries.strings()
			.alpha()
			.numeric()
			.withChars('-')
			.ofMinLength(3)
			.ofMaxLength(63)
			.filter(s -> !s.startsWith("-") && !s.endsWith("-"));

		Arbitrary<String> keyPrefixes = Arbitraries.strings()
			.alpha()
			.numeric()
			.withChars('-', '_')
			.ofMinLength(1)
			.ofMaxLength(50);

		return Combinators.combine(bucketNames, keyPrefixes).as((bucket, prefix) -> new ConfigParams(bucket, prefix));
	}

	record ConfigParams(String bucketName, String keyPrefix) {
	}

}
