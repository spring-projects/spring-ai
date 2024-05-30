package org.springframework.ai.wenxin.metadata.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.wenxin.metadata.WenxinRateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.ai.wenxin.metadata.support.WenxinApiResponseHeaders.REQUESTS_LIMIT_HEADER;
import static org.springframework.ai.wenxin.metadata.support.WenxinApiResponseHeaders.REQUESTS_REMAINING_HEADER;
import static org.springframework.ai.wenxin.metadata.support.WenxinApiResponseHeaders.TOKENS_LIMIT_HEADER;
import static org.springframework.ai.wenxin.metadata.support.WenxinApiResponseHeaders.TOKENS_REMAINING_HEADER;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午5:00
 * @description:
 */
public class WenxinResponseHeaderExtractor {

	private static final Logger logger = LoggerFactory.getLogger(WenxinResponseHeaderExtractor.class);

	public static RateLimit extractAiResponseHeaders(ResponseEntity<?> response) {

		Long requestsLimit = getHeaderAsLong(response, REQUESTS_LIMIT_HEADER.getName());
		Long requestRemaining = getHeaderAsLong(response, REQUESTS_REMAINING_HEADER.getName());
		Long tokensLimit = getHeaderAsLong(response, TOKENS_LIMIT_HEADER.getName());
		Long tokensRemaining = getHeaderAsLong(response, TOKENS_REMAINING_HEADER.getName());

		return new WenxinRateLimit(requestsLimit, requestRemaining, tokensLimit, tokensRemaining);
	}

	private static Long getHeaderAsLong(ResponseEntity<?> response, String headerName) {
		var headers = response.getHeaders();
		if (headers.containsKey(headerName)) {
			var values = headers.get(headerName);
			if (!CollectionUtils.isEmpty(values)) {
				return parseLong(headerName, values.get(0));
			}
		}
		return null;
	}

	private static Long parseLong(String headerName, String headerValue) {

		if (StringUtils.hasText(headerValue)) {
			try {
				return Long.valueOf(headerValue);
			}
			catch (NumberFormatException e) {
				logger.warn("Value [{}] for HTTP header [{}] is not valid: {}", headerName, headerValue,
						e.getMessage());
			}
		}
		return null;
	}

}
