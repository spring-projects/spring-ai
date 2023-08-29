package org.springframework.ai.huggingface.testutils;

import org.springframework.ai.huggingface.client.HuggingfaceAiClient;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractIntegrationTest {

	@Autowired
	protected HuggingfaceAiClient huggingfaceAiClient;

}
