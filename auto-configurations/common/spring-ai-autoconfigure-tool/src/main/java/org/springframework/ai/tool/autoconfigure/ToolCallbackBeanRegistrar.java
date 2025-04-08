package org.springframework.ai.tool.autoconfigure;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code ToolCallbackBeanRegistrar} scans beans after initialization and collects
 * those that have methods annotated with {@link Tool} within the specified base packages.
 *
 * The collected beans are then registered with a {@link ToolCallbackProvider}.
 */
@ConditionalOnClass(Tool.class)
public class ToolCallbackBeanRegistrar implements ApplicationContextAware, BeanPostProcessor {

	private final Set<String> basePackages;

	private ApplicationContext applicationContext;

	private Set<Object> toolBeans = new HashSet<>();


	public ToolCallbackBeanRegistrar(Set<String> basePackages) {
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
				toolBeans.add(bean);
			}
		}

		return bean;
	}

	/**
	 * Checks whether the given package name starts with any of the configured base packages.
	 *
	 * @param packageName the package name to check
	 * @return {@code true} if it is within a configured base package; {@code false} otherwise
	 */
	private boolean isInBasePackage(String packageName) {
		return basePackages.stream().anyMatch(packageName::startsWith);
	}

	/**
	 * Checks if the specified class has any method annotated with {@link Tool}.
	 *
	 * @param clazz the class to inspect
	 * @return {@code true} if at least one method is annotated with {@link Tool}; {@code false} otherwise
	 */
	private boolean hasToolAnnotatedMethod(Class<?> clazz) {
		return Arrays.stream(clazz.getMethods()).anyMatch(method -> method.isAnnotationPresent(Tool.class));
	}

	/**
	 * Creates and registers a {@link MethodToolCallbackProvider} bean, containing all collected
	 * tool beans.
	 *
	 * @return the configured {@link MethodToolCallbackProvider}
	 */
	@Bean
	@ConditionalOnMissingBean
	public MethodToolCallbackProvider methodToolCallbackProvider() {
		MethodToolCallbackProvider.Builder builder = MethodToolCallbackProvider.builder();
		if (!toolBeans.isEmpty()) {
			builder.toolObjects(toolBeans.toArray());
		}

		return builder.build();
	}
}
