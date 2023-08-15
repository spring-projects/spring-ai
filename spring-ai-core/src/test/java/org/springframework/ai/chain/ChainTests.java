package org.springframework.ai.chain;

import org.junit.jupiter.api.Test;
import org.springframework.ai.memory.Memory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class ChainTests {

	@Test
	void badInputs() {
		Chain chain = new FakeChain();
		assertThatThrownBy(() -> chain.apply(Map.of("foobar", "baz"))).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Missing some input keys");
	}

	@Test
	void correctInputs() {
		Chain chain = new FakeChain();
		Map<String, Object> output = chain.apply(Map.of("foo", "bar"));
		assertThat(output).containsEntry("foo", "bar").containsEntry("bar", "baz");
	}

	class FakeChain extends AbstractChain {

		private boolean beCorrect = true;

		private List<String> inputKeys = List.of("foo");

		public FakeChain() {
		}

		public FakeChain(boolean beCorrect) {
			this.beCorrect = beCorrect;
		}

		public FakeChain(List<String> inputKeys) {
			this.inputKeys = inputKeys;
		}

		@Override
		public List<String> getInputKeys() {
			return this.inputKeys;
		}

		@Override
		public List<String> getOutputKeys() {
			return List.of("bar");
		}

		@Override
		protected Map<String, Object> doApply(Map<String, Object> inputMap) {
			if (beCorrect) {
				return Map.of("bar", "baz");
			}
			else {
				return Map.of("baz", "bar");
			}
		}

	}

	class FakeMemory implements Memory {

		@Override
		public List<String> getKeys() {
			return List.of("baz");
		}

		@Override
		public Map<String, Object> load(Map<String, Object> inputs) {
			return Map.of("baz", "foo");
		}

		@Override
		public void save(Map<String, Object> inputs, Map<String, Object> outputs) {

		}

	}

}
