package org.springframework.ai.image;

import org.springframework.ai.model.ModelOptions;

public interface ImageOptions extends ModelOptions {

	Integer getN();

	String getModel();

	Integer getWidth();

	Integer getHeight();

	String getResponseFormat(); // openai - url or base64 : stability ai byte[] or base64

}
