package org.springframework.ai.util;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Utility class that provides predefined SLF4J {@link Marker} instances used in logging
 * operations within the application. <br>
 * This class is not intended to be instantiated.
 */
public class LoggingMarkers {

	/**
	 * Marker instance representing Personally Identifiable Information (PII) used in
	 * logging operations to classify or tag log entries for sensitive data. This can be
	 * utilized to allow selective filtering, handling, or analysis of log messages
	 * containing PII.
	 */
	public static final Marker PII_MARKER = MarkerFactory.getMarker("PII");

	private LoggingMarkers() {
		// Prevent instantiation of this utility class
	}

}
