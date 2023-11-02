package org.springframework.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Map;

public class ConversationBufferMemoryTests {

	@Test
	public void bufferAsString() {
		ConversationBufferMemory memory = new ConversationBufferMemory();
		memory.save(Map.of("input", "foo"), Map.of("output", "bar"));
		assertThat(memory.getBufferAsString()).isEqualTo("""
				user: foo
				assistant: bar""");
	}

	@Test
	public void rolePrefixes() {
		ConversationBufferMemory memory = new ConversationBufferMemory();
		memory.setAiPrefix("bot");
		memory.setHumanPrefix("hooman");
		memory.save(Map.of("input", "foo"), Map.of("output", "bar"));
		assertThat(memory.getBufferAsString()).isEqualTo("""
				hooman: foo
				bot: bar""");
	}

	@Test
	public void memoryKey() {
		ConversationBufferMemory memory = new ConversationBufferMemory();
		memory.setMemoryKey("mem");
		memory.save(Map.of("input", "foo"), Map.of("output", "bar"));
		Map<String, Object> loaded = memory.load(Map.of());
		assertThat(loaded.containsKey("mem"));
		Object mem = loaded.get("mem");
		assertThat(mem).isEqualTo("""
				user: foo
				assistant: bar""");
	}

}
