package org.springframework.ai.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

public class IsoSqlJsonPathFilterExpressionConverterTests {

	@Test
	public void testNIN() {
		final Filter.Expression e = new FilterExpressionTextParser().parse("weather nin [\"windy\", \"rainy\"]");

		final String jsonPathExpression = new IsoSqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("$?( !( @.weather in ( \"windy\",\"rainy\" ) ) )");
	}

	@Test
	public void testNOT() {
		final Filter.Expression e = new FilterExpressionTextParser().parse("NOT( weather in [\"windy\", \"rainy\"] )");

		final String jsonPathExpression = new IsoSqlJsonPathFilterExpressionConverter().convertExpression(e);

		assertThat(jsonPathExpression).isEqualTo("$?( (!( @.weather in ( \"windy\",\"rainy\" ) )) )");
	}

}
