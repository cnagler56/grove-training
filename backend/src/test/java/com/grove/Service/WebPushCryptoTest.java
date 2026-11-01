package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

	/** The worked example in RFC 8291, Appendix A. */
	@Test
	void encryptsExactlyLikeTheStandardsExample() throws Exception {
		byte[] body = WebPushCrypto.encrypt(
			"When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
			"BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
			"BTBZMqHH6r4Tts7J_aSIgg",
			WebPushCrypto.privateKey("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"),
			WebPushCrypto.unb64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"),
			WebPushCrypto.unb64("DGv6ra1nlYgDCS1FRnbzlw"));

		assertThat(WebPushCrypto.b64(body)).isEqualTo(
			"DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_"
			+ "yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
	}

	@Test
	void signsAVapidHeaderTheKeyVerifies() throws Exception {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
		gen.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair pair = gen.generateKeyPair();
		String publicKey = WebPushCrypto.b64(WebPushCrypto.raw((ECPublicKey) pair.getPublic()));
		byte[] d = ((java.security.interfaces.ECPrivateKey) pair.getPrivate()).getS().toByteArray();
		String privateKey = WebPushCrypto.b64(d);

		String header = WebPushCrypto.vapidHeader("https://push.example", "mailto:a@grove.test", 1234567890L, publicKey, privateKey);

		assertThat(header).startsWith("vapid t=").endsWith(", k=" + publicKey);
		String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
		String[] parts = jwt.split("\\.");
		assertThat(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)).contains("ES256");
		assertThat(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
			.contains("\"aud\":\"https://push.example\"", "\"exp\":1234567890", "\"sub\":\"mailto:a@grove.test\"");
		Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
		verify.initVerify(pair.getPublic());
		verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(verify.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
	}
}
