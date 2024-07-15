package org.springframework.ai.ollama;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;

@Disabled("For manual smoke testing only.")
public class OllamaChatModelTests {

	@Test
	void test() {
		var chatModel = new OllamaChatModel(new OllamaApi());
		var models = chatModel.collectModelInformation();
		assert !models.isEmpty();
		for (var model : models) {
			assert model.contextLength() > 0;
		}
	}

}
