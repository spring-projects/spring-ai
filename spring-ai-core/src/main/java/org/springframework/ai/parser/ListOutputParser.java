package org.springframework.ai.parser;

import org.springframework.core.convert.support.DefaultConversionService;

import java.util.List;

/**
 * Parse out a List from a formatting request to convert a
 */
public class ListOutputParser extends AbstractConversionServiceOutputParser<List<String>> {

	public ListOutputParser(DefaultConversionService defaultConversionService) {
		super(defaultConversionService);
	}

	@Override
	public String getFormat() {
		return """
				Your response should be a list of comma separated values
				eg: `foo, bar, baz`
				""";
	}

	@Override
	public List<String> parse(String text) {
		return getConversionService().convert(text, List.class);
	}

}
