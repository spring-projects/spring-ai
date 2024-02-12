package org.springframework.ai;

import org.springframework.ai.annotations.SpringAIFunction;
import org.springframework.ai.model.AbstractToolFunctionCallback;
import org.springframework.ai.model.ToolFunctionCallback;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class SpringAiFunctionManager implements ApplicationContextAware {

	private GenericApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (GenericApplicationContext) applicationContext;
	}

	public int size() {
		return this.applicationContext.getBeansWithAnnotation(SpringAIFunction.class).size();
	}

	/**
	 * @return The list of chat functions
	 */
	public List<ToolFunctionCallback> getAnnotatedToolFunctionCallbacks() {
		var beans = this.applicationContext.getBeansWithAnnotation(SpringAIFunction.class);
		List<ToolFunctionCallback> chatFunctions = new ArrayList<>();
		beans.forEach((k, v) -> {
			if (v instanceof Function<?, ?> function) {
				SpringAIFunction aiFunction = applicationContext.findAnnotationOnBean(k, SpringAIFunction.class);
				chatFunctions.add(new AnnotationGeneratedFunctionCallback(aiFunction.name(), aiFunction.description(),
						aiFunction.classType(), function));
			}
		});

		return chatFunctions;
	}

}

class AnnotationGeneratedFunctionCallback extends AbstractToolFunctionCallback<Object, Object> {

	private Function function;

	protected AnnotationGeneratedFunctionCallback(String name, String description, Class inputType, Function function) {
		super(name, description, inputType);
		this.function = function;
	}

	@Override
	public Object apply(Object o) {
		return function.apply(o);
	}

}
