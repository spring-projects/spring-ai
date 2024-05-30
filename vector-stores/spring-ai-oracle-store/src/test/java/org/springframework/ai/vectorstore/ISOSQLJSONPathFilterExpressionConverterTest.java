package org.springframework.ai.vectorstore;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

import static org.assertj.core.api.Assertions.assertThat;

public class ISOSQLJSONPathFilterExpressionConverterTest {

	private static final Logger logger = LoggerFactory.getLogger(ISOSQLJSONPathFilterExpressionConverterTest.class);

	@Test
	public void testNIN() {
		final Filter.Expression e = new FilterExpressionTextParser().parse("weather nin [\"windy\", \"rainy\"]");

		final String jsonPathExpression = new ISOSQLJSONPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("$?( !( @.weather in ( \"windy\",\"rainy\" ) ) )");
	}

	@Test
	public void testNOT() {
		final Filter.Expression e = new FilterExpressionTextParser().parse("NOT( weather in [\"windy\", \"rainy\"] )");

		final String jsonPathExpression = new ISOSQLJSONPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("$?( (!( @.weather in ( \"windy\",\"rainy\" ) )) )");
	}

}
