package com.grove.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grove.Domain.Dtos.PushSubscribeInput;
import com.grove.Domain.PushSubscription;
import com.grove.Domain.User;
import com.grove.Repository.PushSubscriptionRepository;
import com.grove.Repository.UserRepository;

/**
 * Phone notifications (web push). Each device that turns them on is saved as a
 * subscription; {@link #notify} sends to all of a person's devices in the
 * background, so a slow or broken push service never holds up the action that
 * triggered it. Without VAPID keys set, it does nothing.
 */
@Service
public class PushService {

	private static final Logger log = LoggerFactory.getLogger(PushService.class);
	/** How long the push service keeps trying a phone that's off: a day. */
	private static final int TTL_SECONDS = 24 * 60 * 60;
	private static final Duration VAPID_LIFETIME = Duration.ofHours(12);

	private final PushSubscriptionRepository subscriptions;
	private final UserRepository users;
	private final String publicKey;
	private final String privateKey;
	private final String subject;
	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public PushService(PushSubscriptionRepository subscriptions, UserRepository users,
			@Value("${app.push.public-key:}") String publicKey,
			@Value("${app.push.private-key:}") String privateKey,
			@Value("${app.push.subject:}") String subject) {
		this.subscriptions = subscriptions;
		this.users = users;
		this.publicKey = publicKey.trim();
		this.privateKey = privateKey.trim();
		this.subject = subject.trim();
	}

	public boolean isConfigured() {
		return !publicKey.isEmpty() && !privateKey.isEmpty();
	}

	/** What the browser needs to subscribe; blank when notifications aren't set up. */
	public String publicKey() {
		return isConfigured() ? publicKey : "";
	}

	/** Save this device for the signed-in person. A shared device moves to whoever turned it on last. */
	public void subscribe(User user, PushSubscribeInput input) {
		if (input == null || blank(input.endpoint()) || blank(input.p256dh()) || blank(input.auth())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That device's details are incomplete");
		}
		if (!input.endpoint().startsWith("https://") || input.endpoint().length() > 700) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That device's address isn't valid");
		}
		PushSubscription s = subscriptions.findByEndpoint(input.endpoint()).orElseGet(PushSubscription::new);
		s.setUserId(user.getUserId());
		s.setEndpoint(input.endpoint());
		s.setP256dh(input.p256dh());
		s.setAuth(input.auth());
		if (s.getCreatedAt() == null) s.setCreatedAt(LocalDateTime.now());
		subscriptions.save(s);
	}

	public void unsubscribe(String endpoint) {
		if (!blank(endpoint)) subscriptions.deleteByEndpoint(endpoint);
	}

	/** Tell these people, on every device they've turned on. Disabled accounts are skipped. */
	public void notify(Collection<Long> userIds, String title, String body, String url) {
		if (!isConfigured() || userIds == null || userIds.isEmpty()) return;
		Set<Long> active = users.findAllById(userIds).stream()
			.filter(User::isActive).map(User::getUserId).collect(Collectors.toSet());
		if (active.isEmpty()) return;
		List<PushSubscription> targets = subscriptions.findByUserIdIn(active);
		if (targets.isEmpty()) return;
		byte[] message;
		try {
			message = json.writeValueAsBytes(java.util.Map.of("title", title, "body", body == null ? "" : body, "url", url));
		} catch (JsonProcessingException e) {
			log.warn("Couldn't build a notification", e);
			return;
		}
		CompletableFuture.runAsync(() -> targets.forEach(t -> deliver(t, message)));
	}

	/** Everyone on staff, minus {@code except}. */
	public Set<Long> staffExcept(Long except) {
		return users.findAll().stream()
			.filter(u -> u.isActive() && u.getRoles().isStaff() && !u.getUserId().equals(except))
			.map(User::getUserId).collect(Collectors.toSet());
	}

	private void deliver(PushSubscription target, byte[] message) {
		try {
			URI endpoint = URI.create(target.getEndpoint());
			String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
			long expires = Instant.now().plus(VAPID_LIFETIME).getEpochSecond();
			HttpRequest request = HttpRequest.newBuilder(endpoint)
				.timeout(Duration.ofSeconds(20))
				.header("TTL", String.valueOf(TTL_SECONDS))
				.header("Content-Encoding", "aes128gcm")
				.header("Content-Type", "application/octet-stream")
				.header("Authorization", WebPushCrypto.vapidHeader(audience, subject, expires, publicKey, privateKey))
				.POST(HttpRequest.BodyPublishers.ofByteArray(WebPushCrypto.encrypt(message, target.getP256dh(), target.getAuth())))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			int status = response.statusCode();
			if (status == 404 || status == 410) {
				// The phone turned notifications off or the app was removed.
				subscriptions.deleteByEndpoint(target.getEndpoint());
			} else if (status >= 300) {
				log.warn("Push service answered {}: {}", status, response.body());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.warn("Couldn't send a notification: {}", e.getMessage());
		}
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
