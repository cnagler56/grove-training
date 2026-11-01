package com.grove.Service;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The cryptography web push requires, using only the JDK:
 * message encryption (RFC 8291, aes128gcm) and the server's signed identity
 * (RFC 8292, VAPID). Keys are P-256, passed around as base64url.
 */
final class WebPushCrypto {

	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder UNB64 = Base64.getUrlDecoder();
	private static final int RECORD_SIZE = 4096;
	private static final SecureRandom RANDOM = new SecureRandom();

	private WebPushCrypto() {}

	static String b64(byte[] bytes) {
		return B64.encodeToString(bytes);
	}

	static byte[] unb64(String s) {
		return UNB64.decode(s);
	}

	/** Encrypt a message for one subscription, with a fresh key and salt. */
	static byte[] encrypt(byte[] plaintext, String p256dh, String auth) throws GeneralSecurityException {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
		gen.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair ephemeral = gen.generateKeyPair();
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		return encrypt(plaintext, p256dh, auth, ephemeral.getPrivate(), raw((ECPublicKey) ephemeral.getPublic()), salt);
	}

	/** RFC 8291 section 3.4 with the key and salt supplied (so the RFC's own example can check it). */
	static byte[] encrypt(byte[] plaintext, String p256dh, String auth, PrivateKey asPrivate, byte[] asPublic, byte[] salt)
			throws GeneralSecurityException {
		byte[] uaPublic = unb64(p256dh);
		KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
		agreement.init(asPrivate);
		agreement.doPhase(publicKey(uaPublic), true);
		byte[] ecdhSecret = agreement.generateSecret();

		byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
		byte[] ikm = hkdfExpand(hmac(unb64(auth), ecdhSecret), keyInfo, 32);
		byte[] prk = hmac(salt, ikm);
		byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
		byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

		Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
		aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
		// 0x02 marks the last (only) record.
		byte[] ciphertext = aes.doFinal(concat(plaintext, new byte[] {2}));

		return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + ciphertext.length)
			.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).put(ciphertext)
			.array();
	}

	/** The Authorization header value proving this server sent the message. */
	static String vapidHeader(String audience, String subject, long expiresAtSeconds, String publicKey, String privateKey)
			throws GeneralSecurityException {
		String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
		String claims = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresAtSeconds + ",\"sub\":\"" + subject + "\"}")
			.getBytes(StandardCharsets.UTF_8));
		String unsigned = header + "." + claims;
		Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
		es256.initSign(privateKey(privateKey));
		es256.update(unsigned.getBytes(StandardCharsets.US_ASCII));
		return "vapid t=" + unsigned + "." + b64(es256.sign()) + ", k=" + publicKey;
	}

	static PrivateKey privateKey(String base64url) throws GeneralSecurityException {
		return KeyFactory.getInstance("EC").generatePrivate(
			new ECPrivateKeySpec(new BigInteger(1, unb64(base64url)), p256()));
	}

	static PublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
		if (uncompressed.length != 65 || uncompressed[0] != 4) throw new GeneralSecurityException("Not an uncompressed P-256 key");
		ECPoint point = new ECPoint(
			new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
			new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
		return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, p256()));
	}

	/** A public key as the 65 bytes web push uses: 0x04, then X and Y. */
	static byte[] raw(ECPublicKey key) {
		byte[] out = new byte[65];
		out[0] = 4;
		copyFixed(key.getW().getAffineX(), out, 1);
		copyFixed(key.getW().getAffineY(), out, 33);
		return out;
	}

	private static void copyFixed(BigInteger n, byte[] out, int offset) {
		byte[] b = n.toByteArray();
		int len = Math.min(b.length, 32);
		System.arraycopy(b, b.length - len, out, offset + 32 - len, len);
	}

	private static ECParameterSpec p256() throws GeneralSecurityException {
		AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
		try {
			params.init(new ECGenParameterSpec("secp256r1"));
			return params.getParameterSpec(ECParameterSpec.class);
		} catch (java.security.spec.InvalidParameterSpecException e) {
			throw new GeneralSecurityException(e);
		}
	}

	private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data);
	}

	/** HKDF-Expand for output no longer than one SHA-256 block, which is all web push needs. */
	private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
		return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
	}

	private static byte[] concat(byte[]... parts) {
		int total = 0;
		for (byte[] p : parts) total += p.length;
		ByteBuffer out = ByteBuffer.allocate(total);
		for (byte[] p : parts) out.put(p);
		return out.array();
	}
}
