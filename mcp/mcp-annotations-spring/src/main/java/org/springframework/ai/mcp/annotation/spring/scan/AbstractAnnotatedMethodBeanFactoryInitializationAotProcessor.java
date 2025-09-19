package org.springframework.ai.mcp.annotation.spring.scan;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.log.LogAccessor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Josh Long
 */
public class AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor extends AnnotatedMethodDiscovery
		implements BeanFactoryInitializationAotProcessor {

	private static final LogAccessor logger = new LogAccessor(AbstractAnnotatedMethodBeanPostProcessor.class);

	public AbstractAnnotatedMethodBeanFactoryInitializationAotProcessor(
			Set<Class<? extends Annotation>> targetAnnotations) {
		super(targetAnnotations);
	}

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		List<Class<?>> types = new ArrayList<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			Class<?> beanClass = beanFactory.getType(beanName);
			Set<Class<? extends Annotation>> classes = this.scan(beanClass);
			if (!classes.isEmpty()) {
				types.add(beanClass);
			}
		}
		return (generationContext, beanFactoryInitializationCode) -> {
			RuntimeHints runtimeHints = generationContext.getRuntimeHints();
			for (Class<?> typeReference : types) {
				runtimeHints.reflection().registerType(typeReference, MemberCategory.values());
				logger.info("registering " + typeReference.getName() + " for reflection");
			}
		};
	}

}
