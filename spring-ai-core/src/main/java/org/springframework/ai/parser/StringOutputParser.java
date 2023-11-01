package org.springframework.ai.parser;

import org.springframework.core.convert.support.DefaultConversionService;

public class StringOutputParser extends AbstractConversionServiceOutputParser<String> {

	public StringOutputParser() {
		this(new DefaultConversionService());
	}

	public StringOutputParser(DefaultConversionService conversionService) {
		super(conversionService);
	}

	@Override
	public String getFormat() {
		return """
				Your response should be a string
				eg: `foo`
				""";
	}

	@Override
	public String parse(String text) {
		return getConversionService().convert(text, String.class);
	}

}
