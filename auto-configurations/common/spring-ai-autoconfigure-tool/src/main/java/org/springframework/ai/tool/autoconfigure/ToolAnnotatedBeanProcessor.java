package org.springframework.ai.tool.autoconfigure;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code ToolAnnotatedBeanProcessor} scans beans after initialization and collects those
 * that have methods annotated with {@link Tool} within the specified base packages.
 *
 * The collected beans are then registered with a {@link ToolCallbackProvider}.
 */
public class ToolAnnotatedBeanProcessor
		implements ApplicationContextAware, BeanPostProcessor, SmartInitializingSingleton {

	private final Set<String> basePackages;

	private ApplicationContext applicationContext;

	private Set<Object> methodToolBeans = new HashSet<>();

	public ToolAnnotatedBeanProcessor(Set<String> basePackages) {
		this.basePackages = basePackages;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		Class<?> beanClass = bean.getClass();

		if (isInBasePackage(beanClass.getPackageName())) {

			if (hasToolAnnotatedMethod(beanClass)) {
				methodToolBeans.add(bean);
			}
		}

		return bean;
	}

	/**
	 * Checks whether the given package name starts with any of the configured base
	 * packages.
	 * @param packageName the package name to check
	 * @return {@code true} if it is within a configured base package; {@code false}
	 * otherwise
	 */
	private boolean isInBasePackage(String packageName) {
		return basePackages.stream().anyMatch(packageName::startsWith);
	}

	/**
	 * Checks if the specified class has any method annotated with {@link Tool}.
	 * @param clazz the class to inspect
	 * @return {@code true} if at least one method is annotated with {@link Tool};
	 * {@code false} otherwise
	 */
	private boolean hasToolAnnotatedMethod(Class<?> clazz) {
		return Arrays.stream(clazz.getMethods()).anyMatch(method -> method.isAnnotationPresent(Tool.class));
	}

	/**
	 * Registers a {@link ToolCallbackProvider} bean dynamically after all singleton beans
	 * have been instantiated.
	 *
	 * <p>
	 * This method is invoked by the Spring container at the end of the singleton bean
	 * lifecycle. It collects beans containing methods annotated with {@link Tool}, and
	 * builds a {@link MethodToolCallbackProvider} using those beans. The resulting
	 * provider is registered into the {@link ApplicationContext} as a singleton bean of
	 * type {@link ToolCallbackProvider}.
	 * </p>
	 *
	 * <p>
	 * If no such tool beans are found, or a {@code methodToolCallbackProvider} bean is
	 * already defined in the context, this method does nothing.
	 * </p>
	 */

	@Override
	public void afterSingletonsInstantiated() {

		if (!methodToolBeans.isEmpty()) {
			MethodToolCallbackProvider.Builder builder = MethodToolCallbackProvider.builder();
			builder.toolObjects(methodToolBeans.toArray());
			MethodToolCallbackProvider provider = builder.build();
			ConfigurableListableBeanFactory factory = ((ConfigurableApplicationContext) applicationContext)
				.getBeanFactory();

			if (!factory.containsBean("methodToolCallbackProvider")) {
				factory.registerSingleton("methodToolCallbackProvider", provider);
			}
		}

	}

}
