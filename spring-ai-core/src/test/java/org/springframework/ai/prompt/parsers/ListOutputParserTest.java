package org.springframework.ai.prompt.parsers;

import org.junit.jupiter.api.Test;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListOutputParserTest {

	@Test
	void csv() {
		String csvAsString = "foo, bar, baz";
		ListOutputParser listOutputParser = new ListOutputParser(new DefaultConversionService());
		List<String> list = listOutputParser.parse(csvAsString);
		assertThat(list).containsExactlyElementsOf(List.of("foo", "bar", "baz"));
	}

}