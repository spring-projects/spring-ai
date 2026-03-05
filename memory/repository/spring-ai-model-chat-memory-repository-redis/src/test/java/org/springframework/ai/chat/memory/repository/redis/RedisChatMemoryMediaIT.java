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
package org.springframework.ai.chat.memory.repository.redis;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RedisChatMemoryRepository to verify proper handling of Media
 * content.
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
class RedisChatMemoryMediaIT {

	private static final Logger logger = LoggerFactory.getLogger(RedisChatMemoryMediaIT.class);

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG))
		.withExposedPorts(6379);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private RedisChatMemoryRepository chatMemory;

	private JedisPooled jedisClient;

	@BeforeEach
	void setUp() {
		// Create JedisPooled directly with container properties for reliable connection
		jedisClient = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
		chatMemory = RedisChatMemoryRepository.builder()
			.jedisClient(jedisClient)
			.indexName("test-media-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
			.build();

		// Clear any existing data
		for (String conversationId : chatMemory.findConversationIds()) {
			chatMemory.clear(conversationId);
		}
	}

	@AfterEach
	void tearDown() {
		if (jedisClient != null) {
			jedisClient.close();
		}
	}

	@Test
	void shouldStoreAndRetrieveUserMessageWithUriMedia() {
		this.contextRunner.run(context -> {
			// Create a URI media object
			URI mediaUri = URI.create("https://example.com/image.png");
			Media imageMedia = Media.builder()
				.mimeType(Media.Format.IMAGE_PNG)
				.data(mediaUri)
				.id("test-image-id")
				.name("test-image")
				.build();

			// Create a user message with the media
			UserMessage userMessage = UserMessage.builder()
				.text("Message with image")
				.media(imageMedia)
				.metadata(Map.of("test-key", "test-value"))
				.build();

			// Store the message
			chatMemory.add("test-conversation", userMessage);

			// Retrieve the message
			List<Message> messages = chatMemory.get("test-conversation", 10);

			assertThat(messages).hasSize(1);
			assertThat(messages.get(0)).isInstanceOf(UserMessage.class);

			UserMessage retrievedMessage = (UserMessage) messages.get(0);
			assertThat(retrievedMessage.getText()).isEqualTo("Message with image");
			assertThat(retrievedMessage.getMetadata()).containsEntry("test-key", "test-value");

			// Verify media content
			assertThat(retrievedMessage.getMedia()).hasSize(1);
			Media retrievedMedia = retrievedMessage.getMedia().get(0);
			assertThat(retrievedMedia.getMimeType()).isEqualTo(Media.Format.IMAGE_PNG);
			assertThat(retrievedMedia.getId()).isEqualTo("test-image-id");
			assertThat(retrievedMedia.getName()).isEqualTo("test-image");
			assertThat(retrievedMedia.getData()).isEqualTo(mediaUri.toString());
		});
	}

	@Test
	void shouldStoreAndRetrieveAssistantMessageWithByteArrayMedia() {
		this.contextRunner.run(context -> {
			// Create a byte array media object
			byte[] imageData = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04 };
			Media byteArrayMedia = Media.builder()
				.mimeType(Media.Format.IMAGE_JPEG)
				.data(imageData)
				.id("test-jpeg-id")
				.name("test-jpeg")
				.build();

			// Create a list of tool calls
			List<AssistantMessage.ToolCall> toolCalls = List
				.of(new AssistantMessage.ToolCall("tool1", "function", "testFunction", "{\"param\":\"value\"}"));

			// Create an assistant message with media and tool calls
			AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("Response with image")
				.properties(Map.of("assistant-key", "assistant-value"))
				.toolCalls(toolCalls)
				.media(List.of(byteArrayMedia))
				.build();

			// Store the message
			chatMemory.add("test-conversation", assistantMessage);

			// Retrieve the message
			List<Message> messages = chatMemory.get("test-conversation", 10);

			assertThat(messages).hasSize(1);
			assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);

			AssistantMessage retrievedMessage = (AssistantMessage) messages.get(0);
			assertThat(retrievedMessage.getText()).isEqualTo("Response with image");
			assertThat(retrievedMessage.getMetadata()).containsEntry("assistant-key", "assistant-value");

			// Verify tool calls
			assertThat(retrievedMessage.getToolCalls()).hasSize(1);
			AssistantMessage.ToolCall retrievedToolCall = retrievedMessage.getToolCalls().get(0);
			assertThat(retrievedToolCall.id()).isEqualTo("tool1");
			assertThat(retrievedToolCall.type()).isEqualTo("function");
			assertThat(retrievedToolCall.name()).isEqualTo("testFunction");
			assertThat(retrievedToolCall.arguments()).isEqualTo("{\"param\":\"value\"}");

			// Verify media content
			assertThat(retrievedMessage.getMedia()).hasSize(1);
			Media retrievedMedia = retrievedMessage.getMedia().get(0);
			assertThat(retrievedMedia.getMimeType()).isEqualTo(Media.Format.IMAGE_JPEG);
			assertThat(retrievedMedia.getId()).isEqualTo("test-jpeg-id");
			assertThat(retrievedMedia.getName()).isEqualTo("test-jpeg");
			assertThat(retrievedMedia.getDataAsByteArray()).isEqualTo(imageData);
		});
	}

	@Test
	void shouldStoreAndRetrieveMultipleMessagesWithDifferentMediaTypes() {
		this.contextRunner.run(context -> {
			// Create media objects with different types
			Media pngMedia = Media.builder()
				.mimeType(Media.Format.IMAGE_PNG)
				.data(URI.create("https://example.com/image.png"))
				.id("png-id")
				.build();

			Media jpegMedia = Media.builder()
				.mimeType(Media.Format.IMAGE_JPEG)
				.data(new byte[] { 0x10, 0x20, 0x30, 0x40 })
				.id("jpeg-id")
				.build();

			Media pdfMedia = Media.builder()
				.mimeType(Media.Format.DOC_PDF)
				.data(new ByteArrayResource("PDF content".getBytes()))
				.id("pdf-id")
				.build();

			// Create messages
			UserMessage userMessage1 = UserMessage.builder().text("Message with PNG").media(pngMedia).build();

			AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("Response with JPEG")
				.properties(Map.of())
				.toolCalls(List.of())
				.media(List.of(jpegMedia))
				.build();

			UserMessage userMessage2 = UserMessage.builder().text("Message with PDF").media(pdfMedia).build();

			// Store all messages
			chatMemory.add("media-conversation", List.of(userMessage1, assistantMessage, userMessage2));

			// Retrieve the messages
			List<Message> messages = chatMemory.get("media-conversation", 10);

			assertThat(messages).hasSize(3);

			// Verify first user message with PNG
			UserMessage retrievedUser1 = (UserMessage) messages.get(0);
			assertThat(retrievedUser1.getText()).isEqualTo("Message with PNG");
			assertThat(retrievedUser1.getMedia()).hasSize(1);
			assertThat(retrievedUser1.getMedia().get(0).getMimeType()).isEqualTo(Media.Format.IMAGE_PNG);
			assertThat(retrievedUser1.getMedia().get(0).getId()).isEqualTo("png-id");
			assertThat(retrievedUser1.getMedia().get(0).getData()).isEqualTo("https://example.com/image.png");

			// Verify assistant message with JPEG
			AssistantMessage retrievedAssistant = (AssistantMessage) messages.get(1);
			assertThat(retrievedAssistant.getText()).isEqualTo("Response with JPEG");
			assertThat(retrievedAssistant.getMedia()).hasSize(1);
			assertThat(retrievedAssistant.getMedia().get(0).getMimeType()).isEqualTo(Media.Format.IMAGE_JPEG);
			assertThat(retrievedAssistant.getMedia().get(0).getId()).isEqualTo("jpeg-id");
			assertThat(retrievedAssistant.getMedia().get(0).getDataAsByteArray())
				.isEqualTo(new byte[] { 0x10, 0x20, 0x30, 0x40 });

			// Verify second user message with PDF
			UserMessage retrievedUser2 = (UserMessage) messages.get(2);
			assertThat(retrievedUser2.getText()).isEqualTo("Message with PDF");
			assertThat(retrievedUser2.getMedia()).hasSize(1);
			assertThat(retrievedUser2.getMedia().get(0).getMimeType()).isEqualTo(Media.Format.DOC_PDF);
			assertThat(retrievedUser2.getMedia().get(0).getId()).isEqualTo("pdf-id");
			// Data should be a byte array from the ByteArrayResource
			assertThat(retrievedUser2.getMedia().get(0).getDataAsByteArray()).isEqualTo("PDF content".getBytes());
		});
	}

	@Test
	void shouldStoreAndRetrieveMessageWithMultipleMedia() {
		this.contextRunner.run(context -> {
			// Create multiple media objects
			Media textMedia = Media.builder()
				.mimeType(Media.Format.DOC_TXT)
				.data("This is text content".getBytes())
				.id("text-id")
				.name("text-file")
				.build();

			Media imageMedia = Media.builder()
				.mimeType(Media.Format.IMAGE_PNG)
				.data(URI.create("https://example.com/image.png"))
				.id("image-id")
				.name("image-file")
				.build();

			// Create a message with multiple media attachments
			UserMessage userMessage = UserMessage.builder()
				.text("Message with multiple attachments")
				.media(textMedia, imageMedia)
				.build();

			// Store the message
			chatMemory.add("multi-media-conversation", userMessage);

			// Retrieve the message
			List<Message> messages = chatMemory.get("multi-media-conversation", 10);

			assertThat(messages).hasSize(1);
			UserMessage retrievedMessage = (UserMessage) messages.get(0);
			assertThat(retrievedMessage.getText()).isEqualTo("Message with multiple attachments");

			// Verify multiple media contents
			List<Media> retrievedMedia = retrievedMessage.getMedia();
			assertThat(retrievedMedia).hasSize(2);

			// The media should be retrieved in the same order
			Media retrievedTextMedia = retrievedMedia.get(0);
			assertThat(retrievedTextMedia.getMimeType()).isEqualTo(Media.Format.DOC_TXT);
			assertThat(retrievedTextMedia.getId()).isEqualTo("text-id");
			assertThat(retrievedTextMedia.getName()).isEqualTo("text-file");
			assertThat(retrievedTextMedia.getDataAsByteArray()).isEqualTo("This is text content".getBytes());

			Media retrievedImageMedia = retrievedMedia.get(1);
			assertThat(retrievedImageMedia.getMimeType()).isEqualTo(Media.Format.IMAGE_PNG);
			assertThat(retrievedImageMedia.getId()).isEqualTo("image-id");
			assertThat(retrievedImageMedia.getName()).isEqualTo("image-file");
			assertThat(retrievedImageMedia.getData()).isEqualTo("https://example.com/image.png");
		});
	}

	@Test
	void shouldClearConversationWithMedia() {
		this.contextRunner.run(context -> {
			// Create a message with media
			Media imageMedia = Media.builder()
				.mimeType(Media.Format.IMAGE_PNG)
				.data(new byte[] { 0x01, 0x02, 0x03 })
				.id("test-clear-id")
				.build();

			UserMessage userMessage = UserMessage.builder().text("Message to be cleared").media(imageMedia).build();

			// Store the message
			String conversationId = "conversation-to-clear";
			chatMemory.add(conversationId, userMessage);

			// Verify it was stored
			assertThat(chatMemory.get(conversationId, 10)).hasSize(1);

			// Clear the conversation
			chatMemory.clear(conversationId);

			// Verify it was cleared
			assertThat(chatMemory.get(conversationId, 10)).isEmpty();
			assertThat(chatMemory.findConversationIds()).doesNotContain(conversationId);
		});
	}

	@Test
	void shouldHandleLargeBinaryData() {
		this.contextRunner.run(context -> {
			// Create a larger binary payload (around 50KB)
			byte[] largeImageData = new byte[50 * 1024];
			// Fill with a recognizable pattern for verification
			for (int i = 0; i < largeImageData.length; i++) {
				largeImageData[i] = (byte) (i % 256);
			}

			// Create media with the large data
			Media largeMedia = Media.builder()
				.mimeType(Media.Format.IMAGE_PNG)
				.data(largeImageData)
				.id("large-image-id")
				.name("large-image.png")
				.build();

			// Create a message with large media
			UserMessage userMessage = UserMessage.builder()
				.text("Message with large image attachment")
				.media(largeMedia)
				.build();

			// Store the message
			String conversationId = "large-media-conversation";
			chatMemory.add(conversationId, userMessage);

			// Retrieve the message
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify
			assertThat(messages).hasSize(1);
			UserMessage retrievedMessage = (UserMessage) messages.get(0);
			assertThat(retrievedMessage.getMedia()).hasSize(1);

			// Verify the large binary data was preserved exactly
			Media retrievedMedia = retrievedMessage.getMedia().get(0);
			assertThat(retrievedMedia.getMimeType()).isEqualTo(Media.Format.IMAGE_PNG);
			byte[] retrievedData = retrievedMedia.getDataAsByteArray();
			assertThat(retrievedData).hasSize(50 * 1024);
			assertThat(retrievedData).isEqualTo(largeImageData);
		});
	}

	@Test
	void shouldHandleMediaWithEmptyOrNullValues() {
		this.contextRunner.run(context -> {
			// Create media with null or empty values where allowed
			Media edgeCaseMedia1 = Media.builder()
				.mimeType(Media.Format.IMAGE_PNG) // MimeType is required
				.data(new byte[0]) // Empty byte array
				.id(null) // No ID
				.name("") // Empty name
				.build();

			// Second media with only required fields
			Media edgeCaseMedia2 = Media.builder()
				.mimeType(Media.Format.DOC_TXT) // Only required field
				.data(new byte[0]) // Empty byte array instead of null
				.build();

			// Create message with these edge case media objects
			UserMessage userMessage = UserMessage.builder()
				.text("Edge case media test")
				.media(edgeCaseMedia1, edgeCaseMedia2)
				.build();

			// Store the message
			String conversationId = "edge-case-media";
			chatMemory.add(conversationId, userMessage);

			// Retrieve the message
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify the message was stored and retrieved
			assertThat(messages).hasSize(1);
			UserMessage retrievedMessage = (UserMessage) messages.get(0);

			// Verify the media objects
			List<Media> retrievedMedia = retrievedMessage.getMedia();
			assertThat(retrievedMedia).hasSize(2);

			// Check first media with empty/null values
			Media firstMedia = retrievedMedia.get(0);
			assertThat(firstMedia.getMimeType()).isEqualTo(Media.Format.IMAGE_PNG);
			assertThat(firstMedia.getDataAsByteArray()).isNotNull().isEmpty();
			assertThat(firstMedia.getId()).isNull();
			assertThat(firstMedia.getName()).isEmpty();

			// Check second media with only required field
			Media secondMedia = retrievedMedia.get(1);
			assertThat(secondMedia.getMimeType()).isEqualTo(Media.Format.DOC_TXT);
			assertThat(secondMedia.getDataAsByteArray()).isNotNull().isEmpty();
			assertThat(secondMedia.getId()).isNull();
			assertThat(secondMedia.getName()).isNotNull();
		});
	}

	@Test
	void shouldHandleComplexBinaryDataTypes() {
		this.contextRunner.run(context -> {
			// Create audio sample data (simple WAV header + sine wave)
			byte[] audioData = createSampleAudioData(8000, 2); // 2 seconds of 8kHz audio

			// Create video sample data (mock MP4 data with recognizable pattern)
			byte[] videoData = createSampleVideoData(10 * 1024); // 10KB mock video data

			// Create custom MIME types for specialized formats
			MimeType customAudioType = new MimeType("audio", "wav");
			MimeType customVideoType = new MimeType("video", "mp4");

			// Create media objects with the complex binary data
			Media audioMedia = Media.builder()
				.mimeType(customAudioType)
				.data(audioData)
				.id("audio-sample-id")
				.name("audio-sample.wav")
				.build();

			Media videoMedia = Media.builder()
				.mimeType(customVideoType)
				.data(videoData)
				.id("video-sample-id")
				.name("video-sample.mp4")
				.build();

			// Create messages with the complex media
			UserMessage userMessage = UserMessage.builder()
				.text("Message with audio attachment")
				.media(audioMedia)
				.build();

			AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("Response with video attachment")
				.properties(Map.of())
				.toolCalls(List.of())
				.media(List.of(videoMedia))
				.build();

			// Store the messages
			String conversationId = "complex-media-conversation";
			chatMemory.add(conversationId, List.of(userMessage, assistantMessage));

			// Retrieve the messages
			List<Message> messages = chatMemory.get(conversationId, 10);

			// Verify
			assertThat(messages).hasSize(2);

			// Verify audio data in user message
			UserMessage retrievedUserMessage = (UserMessage) messages.get(0);
			assertThat(retrievedUserMessage.getText()).isEqualTo("Message with audio attachment");
			assertThat(retrievedUserMessage.getMedia()).hasSize(1);

			Media retrievedAudioMedia = retrievedUserMessage.getMedia().get(0);
			assertThat(retrievedAudioMedia.getMimeType().toString()).isEqualTo(customAudioType.toString());
			assertThat(retrievedAudioMedia.getId()).isEqualTo("audio-sample-id");
			assertThat(retrievedAudioMedia.getName()).isEqualTo("audio-sample.wav");
			assertThat(retrievedAudioMedia.getDataAsByteArray()).isEqualTo(audioData);

			// Verify binary pattern data integrity
			byte[] retrievedAudioData = retrievedAudioMedia.getDataAsByteArray();
			// Check RIFF header (first 4 bytes of WAV)
			assertThat(Arrays.copyOfRange(retrievedAudioData, 0, 4)).isEqualTo(new byte[] { 'R', 'I', 'F', 'F' });

			// Verify video data in assistant message
			AssistantMessage retrievedAssistantMessage = (AssistantMessage) messages.get(1);
			assertThat(retrievedAssistantMessage.getText()).isEqualTo("Response with video attachment");
			assertThat(retrievedAssistantMessage.getMedia()).hasSize(1);

			Media retrievedVideoMedia = retrievedAssistantMessage.getMedia().get(0);
			assertThat(retrievedVideoMedia.getMimeType().toString()).isEqualTo(customVideoType.toString());
			assertThat(retrievedVideoMedia.getId()).isEqualTo("video-sample-id");
			assertThat(retrievedVideoMedia.getName()).isEqualTo("video-sample.mp4");
			assertThat(retrievedVideoMedia.getDataAsByteArray()).isEqualTo(videoData);

			// Verify the MP4 header pattern
			byte[] retrievedVideoData = retrievedVideoMedia.getDataAsByteArray();
			// Check mock MP4 signature (first 4 bytes should be ftyp)
			assertThat(Arrays.copyOfRange(retrievedVideoData, 4, 8)).isEqualTo(new byte[] { 'f', 't', 'y', 'p' });
		});
	}

	/**
	 * Creates a sample audio data byte array with WAV format.
	 * @param sampleRate Sample rate of the audio in Hz
	 * @param durationSeconds Duration of the audio in seconds
	 * @return Byte array containing a simple WAV file
	 */
	private byte[] createSampleAudioData(int sampleRate, int durationSeconds) {
		// Calculate sizes
		int headerSize = 44; // Standard WAV header size
		int dataSize = sampleRate * durationSeconds; // 1 byte per sample, mono
		int totalSize = headerSize + dataSize;

		byte[] audioData = new byte[totalSize];

		// Write WAV header (RIFF chunk)
		audioData[0] = 'R';
		audioData[1] = 'I';
		audioData[2] = 'F';
		audioData[3] = 'F';

		// File size - 8 (4 bytes little endian)
		int fileSizeMinus8 = totalSize - 8;
		audioData[4] = (byte) (fileSizeMinus8 & 0xFF);
		audioData[5] = (byte) ((fileSizeMinus8 >> 8) & 0xFF);
		audioData[6] = (byte) ((fileSizeMinus8 >> 16) & 0xFF);
		audioData[7] = (byte) ((fileSizeMinus8 >> 24) & 0xFF);

		// WAVE chunk
		audioData[8] = 'W';
		audioData[9] = 'A';
		audioData[10] = 'V';
		audioData[11] = 'E';

		// fmt chunk
		audioData[12] = 'f';
		audioData[13] = 'm';
		audioData[14] = 't';
		audioData[15] = ' ';

		// fmt chunk size (16 for PCM)
		audioData[16] = 16;
		audioData[17] = 0;
		audioData[18] = 0;
		audioData[19] = 0;

		// Audio format (1 = PCM)
		audioData[20] = 1;
		audioData[21] = 0;

		// Channels (1 = mono)
		audioData[22] = 1;
		audioData[23] = 0;

		// Sample rate
		audioData[24] = (byte) (sampleRate & 0xFF);
		audioData[25] = (byte) ((sampleRate >> 8) & 0xFF);
		audioData[26] = (byte) ((sampleRate >> 16) & 0xFF);
		audioData[27] = (byte) ((sampleRate >> 24) & 0xFF);

		// Byte rate (SampleRate * NumChannels * BitsPerSample/8)
		int byteRate = sampleRate * 1 * 8 / 8;
		audioData[28] = (byte) (byteRate & 0xFF);
		audioData[29] = (byte) ((byteRate >> 8) & 0xFF);
		audioData[30] = (byte) ((byteRate >> 16) & 0xFF);
		audioData[31] = (byte) ((byteRate >> 24) & 0xFF);

		// Block align (NumChannels * BitsPerSample/8)
		audioData[32] = 1;
		audioData[33] = 0;

		// Bits per sample
		audioData[34] = 8;
		audioData[35] = 0;

		// Data chunk
		audioData[36] = 'd';
		audioData[37] = 'a';
		audioData[38] = 't';
		audioData[39] = 'a';

		// Data size
		audioData[40] = (byte) (dataSize & 0xFF);
		audioData[41] = (byte) ((dataSize >> 8) & 0xFF);
		audioData[42] = (byte) ((dataSize >> 16) & 0xFF);
		audioData[43] = (byte) ((dataSize >> 24) & 0xFF);

		// Generate a simple sine wave for audio data
		for (int i = 0; i < dataSize; i++) {
			// Simple sine wave pattern (0-255)
			audioData[headerSize + i] = (byte) (128 + 127 * Math.sin(2 * Math.PI * 440 * i / sampleRate));
		}

		return audioData;
	}

	/**
	 * Creates sample video data with a mock MP4 structure.
	 * @param sizeBytes Size of the video data in bytes
	 * @return Byte array containing mock MP4 data
	 */
	private byte[] createSampleVideoData(int sizeBytes) {
		byte[] videoData = new byte[sizeBytes];

		// Write MP4 header
		// First 4 bytes: size of the first atom
		int firstAtomSize = 24; // Standard size for ftyp atom
		videoData[0] = 0;
		videoData[1] = 0;
		videoData[2] = 0;
		videoData[3] = (byte) firstAtomSize;

		// Next 4 bytes: ftyp (file type atom)
		videoData[4] = 'f';
		videoData[5] = 't';
		videoData[6] = 'y';
		videoData[7] = 'p';

		// Major brand (mp42)
		videoData[8] = 'm';
		videoData[9] = 'p';
		videoData[10] = '4';
		videoData[11] = '2';

		// Minor version
		videoData[12] = 0;
		videoData[13] = 0;
		videoData[14] = 0;
		videoData[15] = 1;

		// Compatible brands (mp42, mp41)
		videoData[16] = 'm';
		videoData[17] = 'p';
		videoData[18] = '4';
		videoData[19] = '2';
		videoData[20] = 'm';
		videoData[21] = 'p';
		videoData[22] = '4';
		videoData[23] = '1';

		// Fill the rest with a recognizable pattern
		for (int i = firstAtomSize; i < sizeBytes; i++) {
			// Create a repeating pattern with some variation
			videoData[i] = (byte) ((i % 64) + ((i / 64) % 64));
		}

		return videoData;
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		RedisChatMemoryRepository chatMemory() {
			return RedisChatMemoryRepository.builder()
				.jedisClient(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()))
				.indexName("test-media-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
				.build();
		}

	}

}
