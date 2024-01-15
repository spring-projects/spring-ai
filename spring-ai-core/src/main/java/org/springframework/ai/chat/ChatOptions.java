package org.springframework.ai.chat;

import org.springframework.ai.generative.Options;

/**
 * portable options
 */
public interface ChatOptions extends Options {

	// determine portable optionsb

	Float getTemperature();

	void setTemperature(Float temperature);

	Float getTopP();

	void setTopP(Float topP);

	Integer getTopK();

	void setTopK(Integer topK);

}
