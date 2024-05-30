package org.springframework.ai.wenxin.api;

import org.apache.commons.codec.binary.Hex;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author lvchzh
 * @date 2024年05月22日 下午2:45
 * @description: ApiUtils
 */
public class ApiUtils {

	// @formatter:off
	public static final String DEFAULT_BASE_URL = "https://aip.baidubce.com";

	public static final String DEFAULT_BASE_CHAT_URI = "/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/";

	public static final String DEFAULT_BASE_EMBEDDING_URI = "/rpc/2.0/ai_custom/v1/wenxinworkshop/embeddings/";

	public static final String DEFAULT_HOST = "aip.baidubce.com";

	private static final String EXPIRATION_PERIOD_IN_SECONDS = "1800";

	private static final String HMAC_SHA256 = "HmacSHA256";

	private static final DateTimeFormatter alternateIso8601DateFormat = DateTimeFormatter.ofPattern(
			"yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

	public static Consumer<HttpHeaders> getJsonContentHeaders() {
		return (headers) -> headers.setContentType(MediaType.APPLICATION_JSON);
	}

	public static String generationSignature(String accessKey, String secretKey, Instant timestamp, String modelName, String uri) {
		var canonicalRequest = createCanonicalRequest(uri, modelName);
		var authStringPrefix = createAuthStringPrefix(accessKey, timestamp);
		var signingKey = hmacSha256Hex(secretKey, authStringPrefix);
		var signature = hmacSha256Hex(signingKey, canonicalRequest.toString());
		return new StringBuilder()
				.append(authStringPrefix)
				.append("/host/")
				.append(signature)
				.toString();
	}

	private static String createAuthStringPrefix(String accessKey, Instant timestamp) {
		return new StringBuilder()
				.append("bce-auth-v1/").append(accessKey)
				.append("/")
				.append(formatDate(timestamp))
				.append("/")
				.append(EXPIRATION_PERIOD_IN_SECONDS)
				.toString();
	}

	private static StringBuilder createCanonicalRequest(String uri, String modelName) {
		return new StringBuilder()
				.append("POST")
				.append("\n")
				.append(uri).append(modelName)
				.append("\n\n")
				.append("host:").append(DEFAULT_HOST);
	}

	private static String hmacSha256Hex(String secretKey, String authStringPrefix) {
		try {
			var mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			return new String(Hex.encodeHex(mac.doFinal(authStringPrefix.getBytes(StandardCharsets.UTF_8))));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException("Failed to generate HMAC-SHA256 signature", e);
		}
	}

	private static Optional<String> formatAlternateIso8601Date(Instant instant) {
		if (instant == null) {
			return Optional.empty();
		}
		return Optional.of(alternateIso8601DateFormat.format(instant));
	}

	public static String formatDate(Instant instant) {
		return formatAlternateIso8601Date(instant).orElseThrow(() -> new RuntimeException("Failed to format date"));
	}

	public static String generationAuthorization(String accessKey, String secretKey, Instant timestamp, String model, String uri) {
		return generationSignature(accessKey, secretKey, timestamp, model, uri);
	}
	// @formatter:on

}
