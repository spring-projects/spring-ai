package org.springframework.ai.google.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * <a href="https://ai.google.dev/api/caching#Schema">Docs</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseSchema {

	/**
	 * Required. Data type.
	 */
	@JsonProperty("type")
	private Type type;

	/**
	 * Optional. The format of the data. Used only for primitive datatypes. Supported
	 * formats: for NUMBER type: float, double; for INTEGER type: int32, int64; for STRING
	 * type: enum, date-time
	 */
	@JsonProperty("format")
	private String format;

	/**
	 * Optional. The title of the schema.
	 */
	@JsonProperty("title")
	private String title;

	/**
	 * Optional. A brief description of the parameter. May be formatted as Markdown.
	 */
	@JsonProperty("description")
	private String description;

	/**
	 * Optional. Indicates if the value may be null.
	 */
	@JsonProperty("nullable")
	private Boolean nullable;

	/**
	 * Optional. Possible values of the element of Type STRING with enum format. For
	 * example, we can define an Enum Direction as: {type:STRING, format:enum,
	 * enum:["EAST", NORTH", "SOUTH", "WEST"]}
	 */
	@JsonProperty("enum")
	private List<String> enumValues;

	/**
	 * Optional. Maximum number of the elements for Type.ARRAY. (int64 format)
	 */
	@JsonProperty("maxItems")
	private String maxItems;

	/**
	 * Optional. Minimum number of the elements for Type.ARRAY. (int64 format)
	 */
	@JsonProperty("minItems")
	private String minItems;

	/**
	 * Optional. Properties of Type.OBJECT. An object containing a list of "key": value
	 * pairs. Example: { "name": "wrench", "mass": "1.3kg", "count": "3" }.
	 */
	@JsonProperty("properties")
	private Map<String, ResponseSchema> properties;

	/**
	 * Optional. Required properties of Type.OBJECT.
	 */
	@JsonProperty("required")
	private List<String> required;

	/**
	 * Optional. Minimum number of the properties for Type.OBJECT. (int64 format)
	 */
	@JsonProperty("minProperties")
	private String minProperties;

	/**
	 * Optional. Maximum number of the properties for Type.OBJECT. (int64 format)
	 */
	@JsonProperty("maxProperties")
	private String maxProperties;

	/**
	 * Optional. Minimum length of the Type.STRING. (int64 format)
	 */
	@JsonProperty("minLength")
	private String minLength;

	/**
	 * Optional. Maximum length of the Type.STRING. (int64 format)
	 */
	@JsonProperty("maxLength")
	private String maxLength;

	/**
	 * Optional. Pattern of the Type.STRING to restrict a string to a regular expression.
	 */
	@JsonProperty("pattern")
	private String pattern;

	/**
	 * Optional. Example of the object. Will only be populated when the object is the
	 * root.
	 */
	@JsonProperty("example")
	private Object example;

	/**
	 * Optional. The value should be validated against any (one or more) of the subschemas
	 * in the list.
	 */
	@JsonProperty("anyOf")
	private List<ResponseSchema> anyOf;

	/**
	 * Optional. The order of the properties. Used to determine the order of the
	 * properties in the response.
	 */
	@JsonProperty("propertyOrdering")
	private List<String> propertyOrdering;

	/**
	 * Optional. Default value of the field. Intended for documentation generators and
	 * doesn't affect validation.
	 */
	@JsonProperty("default")
	private Object defaultValue;

	/**
	 * Optional. Schema of the elements of Type.ARRAY.
	 */
	@JsonProperty("items")
	private ResponseSchema items;

	/**
	 * Optional. Minimum value of the Type.INTEGER and Type.NUMBER.
	 */
	@JsonProperty("minimum")
	private Double minimum;

	/**
	 * Optional. Maximum value of the Type.INTEGER and Type.NUMBER.
	 */
	@JsonProperty("maximum")
	private Double maximum;

	public enum Type {

		@JsonProperty("TYPE_UNSPECIFIED")
		TYPE_UNSPECIFIED, @JsonProperty("STRING")
		STRING, @JsonProperty("NUMBER")
		NUMBER, @JsonProperty("INTEGER")
		INTEGER, @JsonProperty("BOOLEAN")
		BOOLEAN, @JsonProperty("ARRAY")
		ARRAY, @JsonProperty("OBJECT")
		OBJECT, @JsonProperty("NULL")
		NULL

	}
	// Getters and setters omitted for brevity

}
