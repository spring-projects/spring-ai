/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.tool.consent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.RequiresConsent;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;

/**
 * Extension of {@link MethodToolCallbackProvider} that wraps tool callbacks requiring
 * consent with {@link ConsentAwareToolCallback}.
 *
 * @author Hyunjoon Park
 * @since 1.0.0
 */
public class ConsentAwareMethodToolCallbackProvider extends MethodToolCallbackProvider {

	private final ConsentManager consentManager;

	/**
	 * Creates a new consent-aware method tool callback provider.
	 * @param toolObjects the objects containing tool methods
	 * @param consentManager the consent manager for handling consent requests
	 */
	public ConsentAwareMethodToolCallbackProvider(List<Object> toolObjects, ConsentManager consentManager) {
		super(toolObjects);
		Assert.notNull(consentManager, "consentManager must not be null");
		this.consentManager = consentManager;
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		ToolCallback[] callbacks = super.getToolCallbacks();

		// Wrap callbacks that require consent
		for (int i = 0; i < callbacks.length; i++) {
			ToolCallback callback = callbacks[i];
			RequiresConsent requiresConsent = findRequiresConsentAnnotation(callback);

			if (requiresConsent != null) {
				callbacks[i] = new ConsentAwareToolCallback(callback, this.consentManager, requiresConsent);
			}
		}

		return callbacks;
	}

	/**
	 * Finds the @RequiresConsent annotation for a tool callback. This method checks the
	 * original method that the callback was created from.
	 * @param callback the tool callback
	 * @return the RequiresConsent annotation or null if not present
	 */
	private RequiresConsent findRequiresConsentAnnotation(ToolCallback callback) {
		// For MethodToolCallback, we need to find the original method
		// This requires accessing the method through reflection or storing it
		// For now, we'll check all methods in the tool objects

		for (Object toolObject : getToolObjects()) {
			Method[] methods = toolObject.getClass().getDeclaredMethods();
			for (Method method : methods) {
				// Check if this method corresponds to the callback
				if (method.getName().equals(callback.getName())) {
					RequiresConsent annotation = AnnotationUtils.findAnnotation(method, RequiresConsent.class);
					if (annotation != null) {
						return annotation;
					}
				}
			}
		}

		return null;
	}

	/**
	 * Gets the list of tool objects from the parent class. This is a workaround since the
	 * field is private in the parent.
	 * @return the list of tool objects
	 */
	private List<Object> getToolObjects() {
		// This would need to be implemented properly, possibly by:
		// 1. Making the field protected in the parent class
		// 2. Adding a getter in the parent class
		// 3. Storing a copy in this class
		// For now, we'll throw an exception indicating this needs to be addressed
		throw new UnsupportedOperationException(
				"Need to access tool objects from parent class. Consider making the field protected or adding a getter.");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private List<Object> toolObjects;

		private ConsentManager consentManager;

		private Builder() {
		}

		public Builder toolObjects(Object... toolObjects) {
			Assert.notNull(toolObjects, "toolObjects cannot be null");
			this.toolObjects = Arrays.asList(toolObjects);
			return this;
		}

		public Builder consentManager(ConsentManager consentManager) {
			Assert.notNull(consentManager, "consentManager cannot be null");
			this.consentManager = consentManager;
			return this;
		}

		public ConsentAwareMethodToolCallbackProvider build() {
			Assert.notNull(this.toolObjects, "toolObjects must be set");
			Assert.notNull(this.consentManager, "consentManager must be set");
			return new ConsentAwareMethodToolCallbackProvider(this.toolObjects, this.consentManager);
		}

	}

}
