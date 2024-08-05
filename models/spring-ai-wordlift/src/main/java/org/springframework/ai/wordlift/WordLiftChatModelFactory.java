package org.springframework.ai.wordlift;

import org.springframework.ai.wordlift.api.WordLiftApi;
import org.springframework.ai.wordlift.api.common.WordLiftApiConstants;

public class WordLiftChatModelFactory {

  private final String baseUrl;

  public WordLiftChatModelFactory() {
    this(WordLiftApiConstants.DEFAULT_BASE_URL);
  }

  public WordLiftChatModelFactory(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public WordLiftChatModel create(String apiKey, String model) {
    return new WordLiftChatModel(
      new WordLiftApi(this.baseUrl, apiKey),
      WordLiftChatOptions.builder().withModel(model).build()
    );
  }
}
