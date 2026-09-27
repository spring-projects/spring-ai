/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.postgresml;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.util.JsonHelper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * <a href="https://postgresml.org">PostgresML</a> EmbeddingModel
 *
 * @author Toshiaki Maki
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Sebastien Deleuze
 */
public class PostgresMlEmbeddingModel extends AbstractEmbeddingModel implements InitializingBean {

	private static final JsonHelper jsonHelper = new JsonHelper();

	private final PostgresMlEmbeddingOptions options;

	private final JdbcTemplate jdbcTemplate;

	private final boolean createExtension;

	/**
	 * a constructor
	 * @param jdbcTemplate JdbcTemplate
	 */
	public PostgresMlEmbeddingModel(JdbcTemplate jdbcTemplate) {
		this(jdbcTemplate, PostgresMlEmbeddingOptions.builder().build(), false);
	}

	public PostgresMlEmbeddingModel(JdbcTemplate jdbcTemplate, PostgresMlEmbeddingOptions options) {
		this(jdbcTemplate, options, false);
	}

	/**
	 * a PostgresMlEmbeddingModel constructor
	 * @param jdbcTemplate JdbcTemplate to use to interact with the database.
	 * @param options PostgresMlEmbeddingOptions to configure the client.
	 */
	public PostgresMlEmbeddingModel(JdbcTemplate jdbcTemplate, PostgresMlEmbeddingOptions options,
			boolean createExtension) {
		Assert.notNull(jdbcTemplate, "jdbc template must not be null.");
		Assert.notNull(options, "options must not be null.");
		Assert.notNull(options.getTransformer(), "transformer must not be null.");
		Assert.notNull(options.getVectorType(), "vectorType must not be null.");
		Assert.notNull(options.getMetadataMode(), "metadataMode must not be null.");

		this.jdbcTemplate = jdbcTemplate;
		this.options = options;
		this.createExtension = createExtension;
	}

	@Override
	public float[] embed(String text) {
		return this.jdbcTemplate.queryForObject(
				"SELECT pgml.embed(?, ?, ?::JSONB)" + this.options.getVectorType().cast + " AS embedding",
				this.options.getVectorType().rowMapper, this.options.getTransformer(), text,
				jsonHelper.toJson(this.options.getKwargs()));
	}

	@Override
	public String getEmbeddingContent(Document document) {
		Assert.notNull(document, "Document must not be null");
		return document.getFormattedContent(this.options.getMetadataMode());
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document, this.options);
	}

	public float[] embed(Document document, EmbeddingOptions options) {
		Assert.notNull(document, "Document must not be null");
		Assert.notNull(options, "PostgresMlEmbeddingOptions must not be null");
		if (!(options instanceof PostgresMlEmbeddingOptions runtimeOptions)) {
			throw new IllegalArgumentException(
					String.format("Expected options of type %s, but got %s", PostgresMlEmbeddingOptions.class.getName(),
							options == null ? "null" : options.getClass().getName()));
		}

		return this.embed(document.getFormattedContent(runtimeOptions.getMetadataMode()), runtimeOptions);
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {

		final PostgresMlEmbeddingOptions optionsToUse = this.mergeOptions(request.getOptions());

		List<Embedding> data = new ArrayList<>();
		List<float[]> embed = List.of();

		List<String> texts = request.getInstructions();
		if (!CollectionUtils.isEmpty(texts)) {
			embed = this.jdbcTemplate.query(connection -> {
				PreparedStatement preparedStatement = connection.prepareStatement("SELECT pgml.embed(?, text, ?::JSONB)"
						+ optionsToUse.getVectorType().cast + " AS embedding FROM (SELECT unnest(?) AS text) AS texts");
				preparedStatement.setString(1, optionsToUse.getTransformer());
				preparedStatement.setString(2, jsonHelper.toJson(optionsToUse.getKwargs()));
				preparedStatement.setArray(3, connection.createArrayOf("TEXT", texts.toArray(Object[]::new)));
				return preparedStatement;
			}, rs -> {
				List<float[]> result = new ArrayList<>();
				while (rs.next()) {
					result.add(optionsToUse.getVectorType().rowMapper.mapRow(rs, -1));
				}
				return result;
			});
		}

		if (!CollectionUtils.isEmpty(embed)) {
			for (int i = 0; i < embed.size(); i++) {
				data.add(new Embedding(embed.get(i), i));
			}
		}

		Map<String, Object> embeddingMetadata = Map.of("transformer", optionsToUse.getTransformer(), "vector-type",
				optionsToUse.getVectorType().name(), "kwargs", jsonHelper.toJson(optionsToUse.getKwargs()));
		var embeddingResponseMetadata = new EmbeddingResponseMetadata("unknown", new EmptyUsage(), embeddingMetadata);
		return new EmbeddingResponse(data, embeddingResponseMetadata);
	}

	/**
	 * Merge the default and request options.
	 * @param requestOptions request options to merge.
	 * @return the merged options.
	 */
	PostgresMlEmbeddingOptions mergeOptions(@Nullable EmbeddingOptions requestOptions) {

		if (requestOptions == null) {
			return this.options;
		}

		PostgresMlEmbeddingOptions.Builder builder = PostgresMlEmbeddingOptions.builder();

		// PostgresMlEmbeddingOptions disregards base EmbeddingOptions properties
		if (requestOptions instanceof PostgresMlEmbeddingOptions pgOptions) {
			builder
				.transformer(ModelOptionsUtils.mergeOption(pgOptions.getTransformer(), this.options.getTransformer()))
				.vectorType(ModelOptionsUtils.mergeOption(pgOptions.getVectorType(), this.options.getVectorType()))
				.kwargs(ModelOptionsUtils.mergeOption(pgOptions.getKwargs(), this.options.getKwargs()))
				.metadataMode(
						ModelOptionsUtils.mergeOption(pgOptions.getMetadataMode(), this.options.getMetadataMode()));
		}
		else {
			builder.transformer(this.options.getTransformer())
				.vectorType(this.options.getVectorType())
				.kwargs(this.options.getKwargs())
				.metadataMode(this.options.getMetadataMode());
		}

		return builder.build();
	}

	@Override
	public void afterPropertiesSet() {
		if (!this.createExtension) {
			return;
		}
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgml");
		if (StringUtils.hasText(this.options.getVectorType().extensionName)) {
			this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS " + this.options.getVectorType().extensionName);
		}
	}

	public enum VectorType {

		PG_ARRAY("", null, (rs, i) -> {
			Array embedding = rs.getArray("embedding");
			return EmbeddingUtils.toPrimitive((Float[]) embedding.getArray());

		}),

		PG_VECTOR("::vector", "vector", (rs, i) -> {
			String embedding = rs.getString("embedding");
			return EmbeddingUtils.toPrimitive(Arrays.stream((embedding.substring(1, embedding.length() - 1)
				/* remove leading '[' and trailing ']' */.split(","))).map(Float::parseFloat).toList());
		});

		private final String cast;

		private final @Nullable String extensionName;

		private final RowMapper<float[]> rowMapper;

		VectorType(String cast, @Nullable String extensionName, RowMapper<float[]> rowMapper) {
			this.cast = cast;
			this.extensionName = extensionName;
			this.rowMapper = rowMapper;
		}

	}

}
