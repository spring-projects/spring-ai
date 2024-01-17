package org.springframework.ai.image;

import org.springframework.ai.model.ModelOptions;

public interface ImageModelOptions extends ModelOptions {

	Integer getN();

	String getModel();

	String getQuality();

	String getResponseFormat();

	String getSize();

	String getStyle();

	String getUser();

}
