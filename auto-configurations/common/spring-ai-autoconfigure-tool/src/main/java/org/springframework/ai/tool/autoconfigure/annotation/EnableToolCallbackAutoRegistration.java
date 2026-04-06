package org.springframework.ai.tool.autoconfigure.annotation;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.autoconfigure.ToolCallbackAutoRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables automatic registration of {@link Tool}-annotated methods as
 * {@link ToolCallback}s.
 *
 * <p>
 * When this annotation is used on a configuration class, it imports the
 * {@link ToolCallbackAutoRegistrar}, which scans the specified packages for Spring beans
 * containing {@code @Tool}-annotated methods. These beans are then registered as
 * {@link ToolCallbackProvider}s.
 *
 * <p>
 * <b>Usage example:</b>
 * </p>
 * <pre>
 * {@code
 * Configuration
 *

EnableToolCallbackAutoRegistration(basePackages = "com.example.tools")
 * public class MyToolConfig {
 * }
 * }
 * </pre>
 *
 * <p>
 * You can specify packages to scan in one of three ways:
 * <ul>
 * <li>{@code basePackages} - Explicit list of package names</li>
 * <li>{@code value} - Alias for {@code basePackages}</li>
 * <li>{@code basePackageClasses} - Package names inferred from provided classes</li>
 * </ul>
 *
 * @see Tool
 * @see ToolCallback
 * @see ToolCallbackProvider
 * @see ToolCallbackAutoRegistrar
 */

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ToolCallbackAutoRegistrar.class)
public @interface EnableToolCallbackAutoRegistration {

	String[] basePackages() default {};

	String[] value() default {};

	Class<?>[] basePackageClasses() default {};

}
