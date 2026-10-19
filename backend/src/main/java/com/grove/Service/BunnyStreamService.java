package com.grove.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.grove.Domain.Dtos.UploadTicket;

/**
 * Thin client for Bunny Stream (bunny.net), where the video files live.
 *
 * Uploads: we create an empty video in the library, then hand the browser a
 * pre-signed TUS ticket so it uploads straight to Bunny — the file never
 * touches our server (keeps Railway RAM/bandwidth near zero).
 *
 * Playback: the player is Bunny's iframe embed. When the library has embed
 * token authentication on, every URL carries a SHA-256 token that expires,
 * so a copied link stops working after a few hours.
 */
@Service
public class BunnyStreamService {

	private static final String API = "https://video.bunnycdn.com/library/";
	private static final String TUS_ENDPOINT = "https://video.bunnycdn.com/tusupload";
	private static final String EMBED = "https://iframe.mediadelivery.net/embed/";
	private static final Duration UPLOAD_TICKET_LIFETIME = Duration.ofHours(6);
	private static final Duration EMBED_LIFETIME = Duration.ofHours(4);

	private final RestTemplate http;
	private final String libraryId;
	private final String apiKey;
	private final String tokenKey;
	private final String cdnHostname;

	public BunnyStreamService(RestTemplate http,
			@Value("${app.bunny.library-id:}") String libraryId,
			@Value("${app.bunny.api-key:}") String apiKey,
			@Value("${app.bunny.token-key:}") String tokenKey,
			@Value("${app.bunny.cdn-hostname:}") String cdnHostname) {
		this.http = http;
		this.libraryId = libraryId.trim();
		this.apiKey = apiKey.trim();
		this.tokenKey = tokenKey.trim();
		this.cdnHostname = cdnHostname.trim();
	}

	public boolean isConfigured() {
		return !libraryId.isEmpty() && !apiKey.isEmpty();
	}

	/** Bunny's view of one video: its numeric status code and length in seconds. */
	public record BunnyVideo(int status, int lengthSeconds) {}

	/** Create an empty video in the library and return its GUID. */
	public String createVideo(String title) {
		requireConfigured();
		try {
			JsonNode body = http.exchange(API + libraryId + "/videos", HttpMethod.POST,
				new HttpEntity<>(Map.of("title", title), headers()), JsonNode.class).getBody();
			if (body == null || !body.hasNonNull("guid")) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Bunny didn't return a video id");
			}
			return body.get("guid").asText();
		} catch (RestClientException e) {
			throw upstream("create the video", e);
		}
	}

	public BunnyVideo getVideo(String guid) {
		requireConfigured();
		try {
			JsonNode body = http.exchange(API + libraryId + "/videos/" + guid, HttpMethod.GET,
				new HttpEntity<>(headers()), JsonNode.class).getBody();
			if (body == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from Bunny");
			return new BunnyVideo(body.path("status").asInt(0), body.path("length").asInt(0));
		} catch (RestClientException e) {
			throw upstream("check the video", e);
		}
	}

	/** Delete from Bunny. A 404 means it's already gone, which is fine. */
	public void deleteVideo(String guid) {
		if (!isConfigured() || guid == null) return;
		try {
			http.exchange(API + libraryId + "/videos/" + guid, HttpMethod.DELETE,
				new HttpEntity<>(headers()), Void.class);
		} catch (HttpClientErrorException.NotFound e) {
			// already deleted
		} catch (RestClientException e) {
			throw upstream("delete the video", e);
		}
	}

	/** Pre-signed TUS upload credentials: SHA256(libraryId + apiKey + expires + videoId). */
	public UploadTicket uploadTicket(Long ourVideoId, String guid) {
		requireConfigured();
		long expires = Instant.now().plus(UPLOAD_TICKET_LIFETIME).getEpochSecond();
		String signature = sha256Hex(libraryId + apiKey + expires + guid);
		return new UploadTicket(ourVideoId, TUS_ENDPOINT, libraryId, guid, expires, signature);
	}

	/** Player URL. Signed (SHA256(tokenKey + videoId + expires)) when a token key is set. */
	public String embedUrl(String guid) {
		String base = EMBED + libraryId + "/" + guid;
		if (tokenKey.isEmpty()) return base + "?preload=true";
		long expires = Instant.now().plus(EMBED_LIFETIME).getEpochSecond();
		return base + "?token=" + sha256Hex(tokenKey + guid + expires) + "&expires=" + expires + "&preload=true";
	}

	/** Thumbnail URL, or null when the CDN hostname isn't configured. */
	public String thumbnailUrl(String guid) {
		if (cdnHostname.isEmpty() || guid == null) return null;
		return "https://" + cdnHostname + "/" + guid + "/thumbnail.jpg";
	}

	private HttpHeaders headers() {
		HttpHeaders h = new HttpHeaders();
		h.set("AccessKey", apiKey);
		h.setContentType(MediaType.APPLICATION_JSON);
		h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
		return h;
	}

	private void requireConfigured() {
		if (!isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Video hosting isn't set up yet (BUNNY_LIBRARY_ID / BUNNY_API_KEY)");
		}
	}

	private static ResponseStatusException upstream(String what, RestClientException e) {
		System.out.println("[BUNNY] failed to " + what + ": " + e.getMessage());
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Couldn't " + what + " on Bunny Stream");
	}

	static String sha256Hex(String s) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
