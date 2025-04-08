package org.springframework.ai.tool.autoconfigure;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.autoconfigure.annotation.EnableToolCallbackAutoRegistration;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link ImportBeanDefinitionRegistrar} implementation that registers a
 * {@link ToolAnnotatedBeanProcessor} bean based on the metadata from
 * {@link EnableToolCallbackAutoRegistration}.
 *
 * <p>
 * This registrar extracts package scanning information from the annotation attributes and
 * registers a {@link ToolAnnotatedBeanProcessor} to process beans containing
 * {@code @Tool}-annotated methods.
 *
 * @see EnableToolCallbackAutoRegistration
 * @see ToolAnnotatedBeanProcessor
 */
@ConditionalOnClass({ Tool.class, ToolCallbackProvider.class })
public class ToolCallbackAutoRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry,
			BeanNameGenerator importBeanNameGenerator) {

		Set<String> basePackages = getBasePackages(importingClassMetadata);

		GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
		beanDefinition.setBeanClass(ToolAnnotatedBeanProcessor.class);
		beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);

		ConstructorArgumentValues args = new ConstructorArgumentValues();
		args.addGenericArgumentValue(basePackages);
		beanDefinition.setConstructorArgumentValues(args);

		registry.registerBeanDefinition("ToolAnnotatedBeanProcessor", beanDefinition);
	}

	/**
	 * Extracts the base packages to scan from the attributes of
	 * {@link EnableToolCallbackAutoRegistration}. If no base package is specified through
	 * 'value', 'basePackages', or 'basePackageClasses', it falls back to the package of
	 * the class where {@code @EnableToolCallbackAutoRegistration} is declared.
	 * @param importingClassMetadata metadata of the importing configuration class
	 * @return set of base packages to scan
	 */
	private Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
		Map<String, Object> attributes = importingClassMetadata
			.getAnnotationAttributes(EnableToolCallbackAutoRegistration.class.getName());

		Set<String> basePackages = new HashSet<>();

		Object[] valuePackages = (Object[]) attributes.get("value");
		if (valuePackages == null)
			valuePackages = new Object[0];

		for (Object obj : valuePackages) {
			if (obj instanceof String str && !str.isEmpty()) {
				basePackages.add(str);
			}
		}

		Object[] basePackagesAttr = (Object[]) attributes.get("basePackages");
		if (basePackagesAttr == null)
			basePackagesAttr = new Object[0];

		for (Object obj : basePackagesAttr) {
			if (obj instanceof String str && !str.isEmpty()) {
				basePackages.add(str);
			}
		}

		Object[] basePackageClasses = (Object[]) attributes.get("basePackageClasses");
		if (basePackageClasses == null)
			basePackageClasses = new Object[0];

		for (Object obj : basePackageClasses) {
			if (obj instanceof Class<?>) {
				// type casting instead of
				// if (obj instanceof Class<?> clazz)
				// for kotlin classes
				Class<?> clazz = (Class<?>) obj;
				basePackages.add(clazz.getPackage().getName());
			}
		}

		// If no base packages are specified, use the package of the class annotated with
		// @EnableToolCallbackAutoRegistration
		if (basePackages.isEmpty()) {
			String className = importingClassMetadata.getClassName();
			try {
				Class<?> importingClass = Class.forName(className);
				Package pkg = importingClass.getPackage();
				if (pkg != null) {
					basePackages.add(pkg.getName());
				}
			}
			catch (ClassNotFoundException e) {
				throw new IllegalStateException("Could not resolve base package from importing class: " + className, e);
			}
		}

		return basePackages;
	}

}
