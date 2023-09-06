package org.springframework.ai.openai.client;

import com.theokanning.openai.completion.chat.ChatFunction;
import com.theokanning.openai.service.FunctionExecutor;
import org.springframework.ai.annotations.SpringAIFunction;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OpenAiFunctionManager implements ApplicationContextAware {

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
	public FunctionExecutor getFunctionExecutor() {
		var beans = this.applicationContext.getBeansWithAnnotation(SpringAIFunction.class);
		List<ChatFunction> chatFunctions = new ArrayList<>();
		beans.forEach((k, v) -> {
			if (v instanceof Function<?, ?> function) {
				SpringAIFunction aiFunction = applicationContext.findAnnotationOnBean(k, SpringAIFunction.class);
				chatFunctions.add(ChatFunction.builder()
					.name(aiFunction.name())
					.description(aiFunction.description())
					.executor(aiFunction.classType(), (Function<? extends Object, Object>) function)
					.build());
			}

		});
		return new FunctionExecutor(chatFunctions);
	}

}
