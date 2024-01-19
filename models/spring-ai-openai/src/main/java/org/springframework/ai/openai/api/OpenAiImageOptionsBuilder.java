package org.springframework.ai.openai.api;

public class OpenAiImageOptionsBuilder {

	private final OpenAiImageOptionsImpl options;

	private OpenAiImageOptionsBuilder() {
		this.options = new OpenAiImageOptionsImpl();
	}

	public static OpenAiImageOptionsBuilder builder() {
		return new OpenAiImageOptionsBuilder();
	}

	public OpenAiImageOptionsBuilder withN(Integer n) {
		options.setN(n);
		return this;
	}

	public OpenAiImageOptionsBuilder withModel(String model) {
		options.setModel(model);
		return this;
	}

	public OpenAiImageOptionsBuilder withQuality(String quality) {
		options.setQuality(quality);
		return this;
	}

	public OpenAiImageOptionsBuilder withResponseFormat(String responseFormat) {
		options.setResponseFormat(responseFormat);
		return this;
	}

	public OpenAiImageOptionsBuilder withWidth(Integer width) {
		options.setWidth(width);
		return this;
	}

	public OpenAiImageOptionsBuilder withHeight(Integer height) {
		options.setHeight(height);
		return this;
	}

	public OpenAiImageOptionsBuilder withStyle(String style) {
		options.setStyle(style);
		return this;
	}

	public OpenAiImageOptionsBuilder withUser(String user) {
		options.setUser(user);
		return this;
	}

	public OpenAiImageOptions build() {
		return options;
	}

}
