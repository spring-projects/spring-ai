package org.springframework.ai.embedding;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * <a href="https://postgresml.org">PostgresML</a> EmbeddingClient
 *
 * @author Toshiaki Maki
 */
public class PostgresMlEmbeddingClient extends AbstractEmbeddingClient implements InitializingBean {

	private final JdbcTemplate jdbcTemplate;

	private final String transformer;

	private final VectorType vectorType;

	private final String kwargs;

	private final MetadataMode metadataMode;

	public enum VectorType {

		PG_ARRAY("", null, (rs, i) -> {
			Array embedding = rs.getArray("embedding");
			return Arrays.stream((Float[]) embedding.getArray()).map(Float::doubleValue).toList();
		}), PG_VECTOR("::vector", "vector", (rs, i) -> {
			String embedding = rs.getString("embedding");
			return Arrays.stream((embedding.substring(1, embedding.length() - 1)
				/* remove leading '[' and trailing ']' */.split(","))).map(Double::parseDouble).toList();
		});

		private final String cast;

		private final String extensionName;

		private final RowMapper<List<Double>> rowMapper;

		VectorType(String cast, String extensionName, RowMapper<List<Double>> rowMapper) {
			this.cast = cast;
			this.extensionName = extensionName;
			this.rowMapper = rowMapper;
		}

	}

	/**
	 * a constructor
	 * @param jdbcTemplate JdbcTemplate
	 */
	public PostgresMlEmbeddingClient(JdbcTemplate jdbcTemplate) {
		this(jdbcTemplate, "distilbert-base-uncased");
	}

	/**
	 * a constructor
	 * @param jdbcTemplate JdbcTemplate
	 * @param transformer huggingface sentence-transformer name
	 */
	public PostgresMlEmbeddingClient(JdbcTemplate jdbcTemplate, String transformer) {
		this(jdbcTemplate, transformer, VectorType.PG_ARRAY);
	}

	/**
	 * a constructor
	 * @param jdbcTemplate JdbcTemplate
	 * @param transformer huggingface sentence-transformer name
	 * @param vectorType vector type in PostgreSQL
	 */
	public PostgresMlEmbeddingClient(JdbcTemplate jdbcTemplate, String transformer, VectorType vectorType) {
		this(jdbcTemplate, transformer, vectorType, Map.of(), MetadataMode.EMBED);
	}

	/**
	 * a constructor
	 * @param jdbcTemplate JdbcTemplate
	 * @param transformer huggingface sentence-transformer name
	 * @param vectorType vector type in PostgreSQL
	 * @param kwargs optional arguments
	 */
	public PostgresMlEmbeddingClient(JdbcTemplate jdbcTemplate, String transformer, VectorType vectorType,
			Map<String, Object> kwargs, MetadataMode metadataMode) {
		Assert.notNull(jdbcTemplate, "jdbc template must not be null.");
		Assert.notNull(transformer, "transformer must not be null.");
		Assert.notNull(vectorType, "vectorType must not be null.");
		Assert.notNull(kwargs, "kwargs must not be null.");
		Assert.notNull(metadataMode, "metadataMode must not be null.");

		this.jdbcTemplate = jdbcTemplate;
		this.transformer = transformer;
		this.vectorType = vectorType;
		this.metadataMode = metadataMode;
		try {
			this.kwargs = new ObjectMapper().writeValueAsString(kwargs);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public List<Double> embed(String text) {
		return this.jdbcTemplate.queryForObject(
				"SELECT pgml.embed(?, ?, ?::JSONB)" + this.vectorType.cast + " AS embedding", this.vectorType.rowMapper,
				this.transformer, text, this.kwargs);
	}

	@Override
	public List<Double> embed(Document document) {
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@Override
	public List<List<Double>> embed(List<String> texts) {
		if (CollectionUtils.isEmpty(texts)) {
			return List.of();
		}
		return this.jdbcTemplate.query(connection -> {
			PreparedStatement preparedStatement = connection.prepareStatement("SELECT pgml.embed(?, text, ?::JSONB)"
					+ vectorType.cast + " AS embedding FROM (SELECT unnest(?) AS text) AS texts");
			preparedStatement.setString(1, transformer);
			preparedStatement.setString(2, kwargs);
			preparedStatement.setArray(3, connection.createArrayOf("TEXT", texts.toArray(Object[]::new)));
			return preparedStatement;
		}, rs -> {
			List<List<Double>> result = new ArrayList<>();
			while (rs.next()) {
				result.add(vectorType.rowMapper.mapRow(rs, -1));
			}
			return result;
		});
	}

	@Override
	public EmbeddingResponse embedForResponse(List<String> texts) {
		List<Embedding> data = new ArrayList<>();
		List<List<Double>> embed = this.embed(texts);
		for (int i = 0; i < embed.size(); i++) {
			data.add(new Embedding(embed.get(i), i));
		}
		return new EmbeddingResponse(data,
				Map.of("transformer", this.transformer, "vector-type", this.vectorType.name(), "kwargs", this.kwargs));
	}

	@Override
	public void afterPropertiesSet() {
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgml");
		if (StringUtils.hasText(this.vectorType.extensionName)) {
			this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS " + this.vectorType.extensionName);
		}
	}

}
