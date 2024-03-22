package org.springframework.ai.azure.openai.dto;

import com.azure.ai.openai.models.FunctionCall;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AccessibleFunctionCall {

	@JsonProperty(value = "name")
	public String name;

	@JsonProperty(value = "arguments")
	public String arguments;

	public static AccessibleFunctionCall from(FunctionCall function) {
		if (function == null) {
			return null;
		}
		final var functionCall = new AccessibleFunctionCall();
		functionCall.arguments = function.getArguments();
		functionCall.name = function.getName();
		return functionCall;
	}

	public static AccessibleFunctionCall merge(AccessibleFunctionCall left, AccessibleFunctionCall right) {
		final var instance = new AccessibleFunctionCall();
		instance.name = left.name != null ? left.name : right.name;
		if (left.arguments != null && right.arguments != null) {
			instance.arguments = left.arguments + right.arguments;
		}
		else if (left.arguments == null) {
			instance.arguments = right.arguments;
		}
		else {
			instance.arguments = left.arguments;
		}
		return instance;
	}

}
