package org.springframework.ai.watsonx.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import java.util.List;

/**
 * @author Pablo Sanchidrian Herrera
 * @author John Jairo Moreno Rojas
 */
public class WatsonxAIOptionTest {

	@Test
	public void testOptions() {
		WatsonxAIOptions options = WatsonxAIOptions.create()
			.withDecodingMethod("sample")
			.withTemperature(1.2f)
			.withTopK(20)
			.withTopP(0.5f)
			.withMaxNewTokens(100)
			.withMinNewTokens(20)
			.withStopSequences(List.of("\n\n\n"))
			.withRepetitionPenalty(1.1f)
			.withRandomSeed(4);

		var optionsMap = options.toMap();

		assertThat(optionsMap).containsEntry("decoding_method", "sample");
		assertThat(optionsMap).containsEntry("temperature", 1.2);
		assertThat(optionsMap).containsEntry("top_k", 20);
		assertThat(optionsMap).containsEntry("top_p", 0.5);
		assertThat(optionsMap).containsEntry("max_new_tokens", 100);
		assertThat(optionsMap).containsEntry("min_new_tokens", 20);
		assertThat(optionsMap).containsEntry("stop_sequences", List.of("\n\n\n"));
		assertThat(optionsMap).containsEntry("repetition_penalty", 1.1);
		assertThat(optionsMap).containsEntry("random_seed", 4);
	}

	@Test
	public void testOptionsDefault() {
		WatsonxAIOptions options = WatsonxAIOptions.create();

		var optionsMap = options.toMap();

		assertThat(optionsMap).containsEntry("decoding_method", "greedy");
		assertThat(optionsMap).containsEntry("temperature", 0.7);
		assertThat(optionsMap).containsEntry("top_k", 50);
		assertThat(optionsMap).containsEntry("top_p", 1.0);
		assertThat(optionsMap).containsEntry("max_new_tokens", 20);
		assertThat(optionsMap).containsEntry("min_new_tokens", 0);
		assertThat(optionsMap).containsEntry("stop_sequences", List.of());
		assertThat(optionsMap).containsEntry("repetition_penalty", 1.0);
		assertThat(optionsMap).containsEntry("random_seed", null);
	}

	@Test
	public void testFilterOut() {
		WatsonxAIOptions options = WatsonxAIOptions.create().withModel("google/flan-ul2");
		var mappedOptions = WatsonxAIOptions.filterNonSupportedFields(options.toMap());
		assertThat(mappedOptions).doesNotContainEntry("model", "google/flan-ul2");
	}

}
