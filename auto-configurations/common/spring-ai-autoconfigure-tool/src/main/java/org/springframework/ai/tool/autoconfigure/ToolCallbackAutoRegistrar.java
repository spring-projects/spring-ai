package org.springframework.ai.tool.autoconfigure;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.autoconfigure.annotation.EnableToolCallbackAutoRegistration;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ApplicationListener} for {@link ApplicationReadyEvent} that scans for Spring
 * beans with {@link Tool @Tool} annotated methods within specified base packages. It then
 * registers a {@link MethodToolCallbackProvider} bean containing these tools.
 * <p>
 * This registrar is activated when {@link EnableToolCallbackAutoRegistration} is used on
 * a configuration class. It leverages {@link ImportAware} to obtain configuration
 * attributes (like base packages) from the enabling annotation and
 * {@link ApplicationContextAware} to access the application context.
 * <p>
 * The actual scanning and registration lógica happens once the application is fully
 * ready, ensuring all beans are initialized.
 *
 * @see EnableToolCallbackAutoRegistration
 * @see Tool
 * @see MethodToolCallbackProvider
 */

@ConditionalOnClass({ Tool.class, ToolCallbackProvider.class })
public class ToolCallbackAutoRegistrar
		implements ApplicationListener<ApplicationReadyEvent>, ImportAware, ApplicationContextAware {

	private static final Logger logger = LoggerFactory.getLogger(ToolCallbackAutoRegistrar.class);

	private Set<String> basePackages;

	private ApplicationContext applicationContext;

	/**
	 * Sets the {@link AnnotationMetadata} of the
	 * importing @{@link org.springframework.context.annotation.Configuration} class. This
	 * method is called by Spring as part of the {@link ImportAware} contract. It extracts
	 * the {@code basePackages} and other attributes from the
	 * {@link EnableToolCallbackAutoRegistration} annotation.
	 * @param importMetadata metadata of the importing configuration class.
	 */
	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> attributesMap = importMetadata
			.getAnnotationAttributes(EnableToolCallbackAutoRegistration.class.getName());
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(attributesMap);
		this.basePackages = getBasePackages(attributes, importMetadata);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * Handles the {@link ApplicationReadyEvent}, which is published when the application
	 * is ready to service requests. This method performs the scan for {@link Tool @Tool}
	 * annotated methods in beans within the configured base packages and registers a
	 * {@link MethodToolCallbackProvider}.
	 * @param event the {@link ApplicationReadyEvent} signalling that the application is
	 * ready.
	 */
	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		// Ensure this listener reacts only to its own application context's ready event,
		// especially in hierarchical contexts.
		if (!event.getApplicationContext().equals(this.applicationContext)) {
			return;
		}

		logger.debug("Application ready, scanning for @Tool annotated methods in base packages: {}", this.basePackages);

		ConfigurableApplicationContext configurableContext = (ConfigurableApplicationContext) this.applicationContext;
		ConfigurableListableBeanFactory beanFactory = configurableContext.getBeanFactory();

		List<Object> toolBeans = new ArrayList<>();
		String[] beanNames = beanFactory.getBeanDefinitionNames();

		for (String beanName : beanNames) {
			// Check if the bean is a singleton, not abstract, and actually obtainable.
			// This avoids issues with beans that are not yet fully initialized or are
			// infrastructure beans.
			if (beanFactory.isSingleton(beanName) && !beanFactory.getBeanDefinition(beanName).isAbstract()
					&& beanFactory.containsBean(beanName)) {
				Object beanInstance = null;
				try {
					beanInstance = beanFactory.getBean(beanName);
				}
				catch (BeansException e) {
					// Log and continue, as some beans might not be fully ready or are
					// special (e.g., factory beans).
					logger.trace("Could not retrieve bean instance for name '{}' during @Tool scan. Skipping.",
							beanName, e);
					continue;
				}

				// Resolve the target class for AOP proxies to find annotations on the
				// actual class.
				Class<?> targetClass = AopUtils.getTargetClass(beanInstance);

				if (isInBasePackage(targetClass.getPackageName())) {
					if (hasToolAnnotatedMethod(targetClass)) {
						toolBeans.add(beanInstance);
						logger.debug("Found @Tool annotated methods in bean: {} of type {}", beanName,
								targetClass.getName());
					}
				}
			}
		}

		if (!toolBeans.isEmpty()) {
			// If a MethodToolCallbackProvider bean doesn't already exist, register one
			// with the found tools.
			if (!beanFactory.containsBean("methodToolCallbackProvider")) {
				MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
					.toolObjects(toolBeans.toArray())
					.build();
				beanFactory.registerSingleton("methodToolCallbackProvider", provider);
				logger.info("Registered MethodToolCallbackProvider with {} tool bean(s).", toolBeans.size());
			}
			else {
				// If a bean with this name already exists, log a warning.
				// This might happen if the user manually defines a bean with the same
				// name.
				logger.warn(
						"Bean 'methodToolCallbackProvider' already exists. Skipping registration by ToolCallbackAutoRegistrar. "
								+ "If this is unexpected, check your configuration.");
			}
		}
		else {
			logger.debug("No beans with @Tool annotated methods found in the specified base packages.");
			// If no tool beans are found and no provider bean exists, register an empty
			// provider.
			// This ensures that beans depending on MethodToolCallbackProvider can still
			// be autowired.
			if (!beanFactory.containsBean("methodToolCallbackProvider")) {
				MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects().build(); // Empty
				beanFactory.registerSingleton("methodToolCallbackProvider", provider);
				logger.info("Registered an empty MethodToolCallbackProvider as no tool beans were found.");
			}
		}
	}

	/**
	 * Extracts the base packages to scan from the
	 * {@link EnableToolCallbackAutoRegistration} annotation attributes. It considers
	 * {@code value}, {@code basePackages}, and {@code basePackageClasses} attributes. If
	 * no packages are explicitly defined, it falls back to the package of the class
	 * annotated with {@link EnableToolCallbackAutoRegistration}.
	 * @param attributes The attributes of the {@link EnableToolCallbackAutoRegistration}
	 * annotation.
	 * @param importingClassMetadata Metadata of the class that imported this registrar
	 * (the @Configuration class).
	 * @return A set of package names to scan.
	 */
	private Set<String> getBasePackages(AnnotationAttributes attributes, AnnotationMetadata importingClassMetadata) {
		Set<String> packages = new HashSet<>();

		// Extract packages from 'value' attribute
		for (String pkg : attributes.getStringArray("value")) {
			if (pkg != null && !pkg.isEmpty()) {
				packages.add(pkg);
			}
		}
		// Extract packages from 'basePackages' attribute
		for (String pkg : attributes.getStringArray("basePackages")) {
			if (pkg != null && !pkg.isEmpty()) {
				packages.add(pkg);
			}
		}
		// Extract packages from 'basePackageClasses' attribute
		for (Class<?> clazz : attributes.getClassArray("basePackageClasses")) {
			packages.add(clazz.getPackage().getName());
		}

		// Fallback: If no packages are specified, use the package of the importing
		// @Configuration class.
		if (packages.isEmpty() && importingClassMetadata != null) {
			String className = importingClassMetadata.getClassName();
			try {
				Class<?> importingClass = Class.forName(className);
				Package pkg = importingClass.getPackage();
				if (pkg != null) {
					packages.add(pkg.getName());
					logger.debug(
							"No explicit base packages configured. Using package of @EnableToolCallbackAutoRegistration class: {}",
							pkg.getName());
				}
			}
			catch (ClassNotFoundException e) {
				logger.warn("Could not resolve base package from importing class: {}", className, e);
			}
		}

		if (packages.isEmpty()) {
			logger.warn("No base packages configured for @Tool scanning. Scanning will be effectively disabled.");
		}
		return packages;
	}

	/**
	 * Checks if the given package name is within any of the configured base packages.
	 * @param packageName The package name to check.
	 * @return {@code true} if the package name starts with any of the configured base
	 * packages, {@code false} otherwise. Returns {@code false} if no base packages are
	 * defined.
	 */
	private boolean isInBasePackage(String packageName) {
		if (this.basePackages == null || this.basePackages.isEmpty()) {
			return false; // No scanning if no base packages are defined.
		}
		return this.basePackages.stream()
			.anyMatch(basePackage -> packageName != null && packageName.startsWith(basePackage));
	}

	/**
	 * Checks if the given class (or any of its superclasses/interfaces) has at least one
	 * method annotated with {@link Tool @Tool}.
	 * @param clazz The class to inspect.
	 * @return {@code true} if at least one {@link Tool @Tool} annotated method is found,
	 * {@code false} otherwise.
	 */
	private boolean hasToolAnnotatedMethod(Class<?> clazz) {
		return Arrays.stream(clazz.getMethods()).anyMatch(method -> method.isAnnotationPresent(Tool.class));
	}

}
