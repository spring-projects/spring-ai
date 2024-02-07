package org.springframework.ai.document.id;

import org.springframework.ai.document.id.impl.JdkSha256HexIdGenerator;
import org.springframework.ai.document.id.impl.RandomIdGenerator;

import java.util.Map;
import java.util.Optional;

public class IdGeneratorProvider {

	private IdGeneratorProvider() {
	}

	private static final IdGeneratorProvider INSTANCE = new IdGeneratorProvider();

	private final Map<IdGeneratorType, IdGenerator> generatorMap = Map.of(IdGeneratorType.RANDOM,
			new RandomIdGenerator(), IdGeneratorType.JDK_SHA_256_HEX, new JdkSha256HexIdGenerator());

	public static IdGeneratorProvider getInstance() {
		return INSTANCE;
	}

	public IdGenerator get(IdGeneratorType idGeneratorType) {
		return Optional.ofNullable(idGeneratorType)
			.filter(generatorMap::containsKey)
			.map(generatorMap::get)
			.orElseThrow();
	}

	public IdGenerator getDefault() {
		return get(IdGeneratorType.JDK_SHA_256_HEX);
	}

}
