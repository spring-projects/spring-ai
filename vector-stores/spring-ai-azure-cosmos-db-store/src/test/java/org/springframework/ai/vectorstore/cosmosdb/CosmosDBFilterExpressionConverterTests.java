package org.springframework.ai.vectorstore.cosmosdb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CosmosDBFilterExpressionConverter.
 * Verifies constructor robustness and doKey logic refactoring without Azure connection.
 */
class CosmosDBFilterExpressionConverterTests {

	@Test
	@DisplayName("Constructor should handle duplicate columns without exception using distinct()")
	void constructor_ShouldHandleDuplicateColumns() {
		// given
		List<String> duplicateColumns = List.of("country", "year", "country"); // Duplicate 'country'

		// when & then
		assertThatCode(() -> new CosmosDBFilterExpressionConverter(duplicateColumns))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Constructor should throw exception when input columns are null")
	void constructor_ShouldThrowExceptionOnNullInput() {
		assertThatThrownBy(() -> new CosmosDBFilterExpressionConverter(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Columns must not be null");
	}

	@Test
	@DisplayName("doKey should correctly convert valid metadata fields")
	void doKey_ShouldConvertValidMetadataField() {
		// given
		var converter = new CosmosDBFilterExpressionConverter(List.of("country"));
		// Use public API convertExpression to invoke protected doKey
		Filter.Expression expression = new Filter.Expression(
				Filter.ExpressionType.EQ,
				new Filter.Key("country"),
				new Filter.Value("Korea")
		);

		// when
		String result = converter.convertExpression(expression);

		// then
		// Verify "c.metadata.country" is generated
		assertThat(result).contains("c.metadata.country");
	}

	@Test
	@DisplayName("doKey should throw clear exception for unknown keys")
	void doKey_ShouldThrowExceptionForUnknownKey() {
		// given
		var converter = new CosmosDBFilterExpressionConverter(List.of("country"));

		// "city" is not configured in metadata fields
		Filter.Expression expression = new Filter.Expression(
				Filter.ExpressionType.EQ,
				new Filter.Key("city"),
				new Filter.Value("Seoul")
		);

		// when & then
		assertThatThrownBy(() -> converter.convertExpression(expression))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("No metadata field city has been configured");
	}
}
