package org.springframework.ai.hunyuan.api.auth;

import org.springframework.ai.hunyuan.api.HunYuanApi;
import org.springframework.ai.hunyuan.api.HunYuanConstants;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import static org.springframework.ai.hunyuan.api.HunYuanConstants.CT_JSON;

public class HunYuanAuthApi {
	private final static Charset UTF8 = StandardCharsets.UTF_8;

	private final String secretId;

	private  final String secretKey;

	public HunYuanAuthApi(String secretId, String secretKey) {
		this.secretId = secretId;
		this.secretKey = secretKey;
	}

	public  byte[] hmac256(byte[] key, String msg){
		Mac mac = null;
		try {
			mac = Mac.getInstance("HmacSHA256");
			SecretKeySpec secretKeySpec = new SecretKeySpec(key, mac.getAlgorithm());
			mac.init(secretKeySpec);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return mac.doFinal(msg.getBytes(UTF8));
	}

	public  String sha256Hex(String s) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		byte[] d = md.digest(s.getBytes(UTF8));
		return DatatypeConverter.printHexBinary(d).toLowerCase();
	}

	public MultiValueMap<String, String> getHttpHeadersConsumer(String host, String action, String service, HunYuanApi.ChatCompletionRequest payload){
		String version = HunYuanConstants.DEFAULT_VERSION;
		String algorithm = HunYuanConstants.DEFAULT_ALGORITHM;
//		String timestamp = "1551113065";
		String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		// 注意时区，否则容易出错
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		String date = sdf.format(new Date(Long.valueOf(timestamp + "000")));

		// ************* 步骤 1：拼接规范请求串 *************
		String httpRequestMethod = "POST";
		String canonicalUri = "/";
		String canonicalQueryString = "";
		String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
				+ "host:" + host + "\n" + "x-tc-action:" + action.toLowerCase() + "\n";
		String signedHeaders = "content-type;host;x-tc-action";

//		String payload = "{\"Limit\": 1, \"Filters\": [{\"Values\": [\"\\u672a\\u547d\\u540d\"], \"Name\": \"instance-name\"}]}";
		String payloadString = ModelOptionsUtils.toJsonString(payload);
		System.out.println(payloadString);
		String hashedRequestPayload = sha256Hex(payloadString);
		String canonicalRequest = httpRequestMethod + "\n" + canonicalUri + "\n" + canonicalQueryString + "\n"
				+ canonicalHeaders + "\n" + signedHeaders + "\n" + hashedRequestPayload;
		// ************* 步骤 2：拼接待签名字符串 *************
		String credentialScope = date + "/" + service + "/" + "tc3_request";
		String hashedCanonicalRequest = sha256Hex(canonicalRequest);
		String stringToSign = algorithm + "\n" + timestamp + "\n" + credentialScope + "\n" + hashedCanonicalRequest;
		System.out.println(stringToSign);
		// ************* 步骤 3：计算签名 *************
		byte[] secretDate = hmac256(("TC3" + secretKey).getBytes(UTF8), date);
		byte[] secretService = hmac256(secretDate, service);
		byte[] secretSigning = hmac256(secretService, "tc3_request");
		String signature = DatatypeConverter.printHexBinary(hmac256(secretSigning, stringToSign)).toLowerCase();
		System.out.println(signature);
		// ************* 步骤 4：拼接 Authorization *************
		String authorization = algorithm + " " + "Credential=" + secretId + "/" + credentialScope + ", "
				+ "SignedHeaders=" + signedHeaders + ", " + "Signature=" + signature;

		TreeMap<String, String> headers = new TreeMap<String, String>();
		headers.put("Authorization", authorization);
		headers.put("Content-Type", CT_JSON);
//		headers.put("Host", host);
		headers.put("X-TC-Action", action);
		headers.put("X-TC-Timestamp", timestamp);
		headers.put("X-TC-Version", version);

		StringBuilder sb = new StringBuilder();
		sb.append("curl -X POST https://").append(host)
				.append(" -H \"Authorization: ").append(authorization).append("\"")
				.append(" -H \"Content-Type: application/json; charset=utf-8\"")
				.append(" -H \"Host: ").append(host).append("\"")
				.append(" -H \"X-TC-Action: ").append(action).append("\"")
				.append(" -H \"X-TC-Timestamp: ").append(timestamp).append("\"")
				.append(" -H \"X-TC-Version: ").append(version).append("\"")
				.append(" -d '").append(payloadString).append("'");
		System.out.println(sb.toString());

		return CollectionUtils.toMultiValueMap(
				headers.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue()))));
	}

}
