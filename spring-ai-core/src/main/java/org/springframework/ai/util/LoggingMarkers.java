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

package org.springframework.ai.util;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Utility class that provides predefined SLF4J {@link Marker} instances used in logging
 * operations within the application. <br>
 * This class is not intended to be instantiated, but is open for extension.
 *
 * @author Konstantin Pavlov
 */
public class LoggingMarkers {

	/**
	 * Marker used to identify log statements associated with <strong>sensitive
	 * data</strong>, such as:
	 * <ul>
	 * <li>Internal business information</li>
	 * <li>Employee data</li>
	 * <li>Customer non-regulated data</li>
	 * <li>Business processes and logic</li>
	 * <li>etc.</li>
	 * </ul>
	 * Typically, logging this information should be avoided.
	 */
	public static final Marker SENSITIVE_DATA_MARKER = MarkerFactory.getMarker("SENSITIVE");

	/**
	 * Marker used to identify log statements associated with <strong>restricted
	 * data</strong>, such as:
	 * <ul>
	 * <li>Authentication credentials</li>
	 * <li>Keys and secrets</li>
	 * <li>Core intellectual property</li>
	 * <li>Critical security configs</li>
	 * <li>Trade secrets</li>
	 * <li>etc.</li>
	 * </ul>
	 * Logging of such information is usually prohibited in any circumstances.
	 */
	public static final Marker RESTRICTED_DATA_MARKER = MarkerFactory.getMarker("RESTRICTED");

	/**
	 * Marker used to identify log statements associated with <strong>regulated
	 * data</strong>, such as:
	 * <ul>
	 * <li>PCI (credit card data)</li>
	 * <li>PHI (health information)</li>
	 * <li>PII (personally identifiable info)</li>
	 * <li>Financial records</li>
	 * <li>Compliance-controlled data</li>
	 * <li>etc.</li>
	 * </ul>
	 * Logging of such information should be avoided.
	 */
	public static final Marker REGULATED_DATA_MARKER = MarkerFactory.getMarker("REGULATED");

	/**
	 * Marker used to identify log statements associated with <strong>public
	 * data</strong>, such as:
	 * <ul>
	 * <li>Public documentation</li>
	 * <li>Marketing materials</li>
	 * <li>etc.</li>
	 * </ul>
	 * There are no restriction for logging such information.
	 */
	public static final Marker PUBLIC_DATA_MARKER = MarkerFactory.getMarker("PUBLIC");

}
