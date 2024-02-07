package org.springframework.ai.document.id;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class IdGeneratorProviderTest {

	// Testing the main goal of issue #113
	// (https://github.com/spring-projects/spring-ai/issues/113)
	@Test
	void similar_ids_for_similar_content_with_consecutive_calls_of_non_random_generators_are_returned() {
		// Initialize
		final IdGeneratorType[] idGeneratorTypes = IdGeneratorType.values();
		final String content = "Content";
		final Map<String, Object> metadata = Map.of("metadata", Set.of("META_DATA"));

		// Run
		final List<String> actualHashes1 = Stream.of(idGeneratorTypes)
			.map(IdGeneratorProvider.getInstance()::get)
			.map(idGenerator -> idGenerator.generateIdFrom(content, metadata))
			.distinct()
			.toList();
		final List<String> actualHashes2 = Stream.of(idGeneratorTypes)
			.map(IdGeneratorProvider.getInstance()::get)
			.map(idGenerator -> idGenerator.generateIdFrom(content, metadata))
			.distinct()
			.toList();

		// Assert (main condition to be tested)
		IntStream.range(1, actualHashes1.size())
			.forEach(i -> Assertions.assertEquals(actualHashes1.get(i), actualHashes2.get(i)));

		// Assert (other expected behaviors)
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes1.get(0)));
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes1.get(1)));
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes2.get(0)));
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes2.get(1)));
	}

	@Test
	void different_ids_for_different_content_with_consecutive_calls_of_each_generator_are_returned() {
		// Initialize
		final IdGeneratorType[] idGeneratorTypes = IdGeneratorType.values();
		final String content = "Content";
		final Map<String, Object> metadata = Map.of("metadata", Set.of("META_DATA"));
		final String content2 = content + " ";
		final Map<String, Object> metadata2 = metadata;

		// Run
		final List<String> actualHashes1 = Stream.of(idGeneratorTypes)
			.map(IdGeneratorProvider.getInstance()::get)
			.map(idGenerator -> idGenerator.generateIdFrom(content, metadata))
			.distinct()
			.toList();
		final List<String> actualHashes2 = Stream.of(idGeneratorTypes)
			.map(IdGeneratorProvider.getInstance()::get)
			.map(idGenerator -> idGenerator.generateIdFrom(content2, metadata2))
			.distinct()
			.toList();

		// Assert (main condition to be tested)
		IntStream.range(0, actualHashes1.size())
			.forEach(i -> Assertions.assertNotEquals(actualHashes1.get(i), actualHashes2.get(i)));

		// Assert (other expected behaviors)
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes1.get(0)));
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes1.get(1)));
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes2.get(0)));
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes2.get(1)));
	}

	@Test
	void expected_ids_with_each_generator_are_returned() {
		// Initialize
		final IdGeneratorType[] idGeneratorTypes = IdGeneratorType.values();
		final String content = "Content";
		final Map<String, Object> metadata = Map.of("metadata", Set.of("META_DATA"));

		final String expectedHashJdkSha256Hex = "cffcc5cc-b45d-3e8b-8513-7396ffbee27e";

		// Run
		final List<String> actualHashes = Stream.of(idGeneratorTypes)
			.map(IdGeneratorProvider.getInstance()::get)
			.map(idGenerator -> idGenerator.generateIdFrom(content, metadata))
			.distinct()
			.toList();

		// Assert
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes.get(0)));
		Assertions.assertDoesNotThrow(() -> UUID.fromString(actualHashes.get(1)));
		Assertions.assertEquals(expectedHashJdkSha256Hex, actualHashes.get(1));
		Assertions.assertEquals(2, actualHashes.size());
	}

	@Test
	void distinct_generators_for_all_types_exist() {
		// Initialize
		final IdGeneratorType[] idGeneratorTypes = IdGeneratorType.values();

		// Run
		final List<Class<?>> generatorClasses = Stream.of(idGeneratorTypes)
			.map(IdGeneratorProvider.getInstance()::get)
			.map(Object::getClass)
			.distinct()
			.collect(Collectors.toUnmodifiableList());

		// Assert
		Assertions.assertEquals(2, idGeneratorTypes.length);
		Assertions.assertEquals(idGeneratorTypes.length, generatorClasses.size());
	}

}