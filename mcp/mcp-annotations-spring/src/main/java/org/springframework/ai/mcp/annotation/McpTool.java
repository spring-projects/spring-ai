/*
 * Copyright 2025-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

/**
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

	/**
	 * The name of the tool. If not provided, the method name will be used.
	 */
	String name() default "";

	/**
	 * The description of the tool. If not provided, the method name will be used.
	 */
	String description() default "";

	/**
	 * Additional hints for clients.
	 */
	McpAnnotations annotations() default @McpAnnotations;

	/**
	 * If true, the tool will generate an output schema for non-primitive output types. If
	 * false, the tool will not automatically generate an output schema.
	 */
	boolean generateOutputSchema() default false;

	/**
	 * Intended for UI and end-user contexts — optimized to be human-readable and easily
	 * understood, even by those unfamiliar with domain-specific terminology. If not
	 * provided, the name should be used for display (except for Tool, where
	 * annotations.title should be given precedence over using name, if present).
	 */
	String title() default "";

	/**
	 * "_meta" field for the tool declaration. If not provided, no "_meta" appended to the
	 * tool specification.
	 */
	Class<? extends MetaProvider> metaProvider() default DefaultMetaProvider.class;

	/**
	 * Additional properties describing a Tool to clients.
	 *
	 * all properties in ToolAnnotations are hints. They are not guaranteed to provide a
	 * faithful description of tool behavior (including descriptive properties like
	 * title).
	 *
	 * Clients should never make tool use decisions based on ToolAnnotations received from
	 * untrusted servers.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.ANNOTATION_TYPE)
	public @interface McpAnnotations {

		/**
		 * A human-readable title for the tool.
		 */
		String title() default "";

		/**
		 * If true, the tool does not modify its environment.
		 */
		boolean readOnlyHint() default false;

		/**
		 * If true, the tool may perform destructive updates to its environment. If false,
		 * the tool performs only additive updates.
		 *
		 * (This property is meaningful only when readOnlyHint == false)
		 */
		boolean destructiveHint() default true;

		/**
		 * If true, calling the tool repeatedly with the same arguments will have no
		 * additional effect on the its environment.
		 *
		 * (This property is meaningful only when readOnlyHint == false)
		 */
		boolean idempotentHint() default false;

		/**
		 * If true, this tool may interact with an “open world” of external entities. If
		 * false, the tool’s domain of interaction is closed. For example, the world of a
		 * web search tool is open, whereas that of a memory tool is not.
		 */
		boolean openWorldHint() default true;

	}

}
