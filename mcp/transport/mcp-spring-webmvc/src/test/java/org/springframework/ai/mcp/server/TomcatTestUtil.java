/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.mcp.server;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * @author Christian Tzolov
 */
public final class TomcatTestUtil {

	private TomcatTestUtil() {
		// Prevent instantiation
	}

	public static TomcatServer createTomcatServer(String contextPath, int port, Class<?> componentClass) {

		// Set up Tomcat first
		var tomcat = new Tomcat();
		tomcat.setPort(port);

		// Set Tomcat base directory to java.io.tmpdir to avoid permission issues
		String baseDir = System.getProperty("java.io.tmpdir");
		tomcat.setBaseDir(baseDir);

		// Use the same directory for document base
		Context context = tomcat.addContext(contextPath, baseDir);

		// Create and configure Spring WebMvc context
		var appContext = new AnnotationConfigWebApplicationContext();
		appContext.register(componentClass);
		appContext.setServletContext(context.getServletContext());
		appContext.refresh();

		// Create DispatcherServlet with our Spring context
		DispatcherServlet dispatcherServlet = new DispatcherServlet(appContext);

		// Add servlet to Tomcat and get the wrapper
		var wrapper = Tomcat.addServlet(context, "dispatcherServlet", dispatcherServlet);
		wrapper.setLoadOnStartup(1);
		wrapper.setAsyncSupported(true);
		context.addServletMappingDecoded("/*", "dispatcherServlet");

		try {
			// Configure and start the connector with async support
			var connector = tomcat.getConnector();
			connector.setAsyncTimeout(3000); // 3 seconds timeout for async requests
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to start Tomcat", e);
		}

		return new TomcatServer(tomcat, appContext);
	}

	public record TomcatServer(Tomcat tomcat, AnnotationConfigWebApplicationContext appContext) {
	}

}
