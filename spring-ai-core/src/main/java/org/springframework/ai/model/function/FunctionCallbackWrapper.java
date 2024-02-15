package org.springframework.ai.model.function;

import java.util.function.Function;

import org.springframework.util.Assert;

/**
 * Note that the underlying function is responsible for converting the output into format
 * that can be consumed by the Model. The default implementation converts the output into
 * String before sending it to the Model. Provide a custom function responseConverter
 * implementation to override this.
 *
 */
public class FunctionCallbackWrapper<I, O> extends AbstractFunctionCallback<I, O> {

	private Function<I, O> function;

	public FunctionCallbackWrapper(String name, String description, Class<I> inputType, Function<I, O> function) {
		super(name, description, inputType);
		Assert.notNull(function, "Function must not be null");
		this.function = function;
	}

	public FunctionCallbackWrapper(String name, String description, Class<I> inputType,
			Function<O, String> responseConverter, Function<I, O> function) {
		super(name, description, inputType, responseConverter);
		Assert.notNull(function, "Function must not be null");
		this.function = function;
	}

	public FunctionCallbackWrapper(String name, String description, Function<I, O> function) {
		this(name, description, resolveInputType(function), function);
	}

	public FunctionCallbackWrapper(String name, String description, Function<O, String> responseConverter,
			Function<I, O> function) {
		this(name, description, resolveInputType(function), responseConverter, function);
	}

	@SuppressWarnings("unchecked")
	private static <I, O> Class<I> resolveInputType(Function<I, O> function) {
		return (Class<I>) TypeResolverHelper.getFunctionInputClass((Class<Function<I, O>>) function.getClass());
	}

	@Override
	public O apply(I input) {
		return this.function.apply(input);
	}

}