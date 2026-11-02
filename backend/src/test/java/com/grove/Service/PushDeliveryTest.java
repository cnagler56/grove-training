package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import com.grove.Domain.PushSubscription;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.PushSubscriptionRepository;
import com.grove.Repository.UserRepository;
import com.sun.net.httpserver.HttpServer;

/** Sends to a stand-in push server. The keys are RFC 8291's published example keys, not real ones. */
@SpringBootTest(properties = {
	"app.push.public-key=BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
	"app.push.private-key=yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
	"app.push.subject=mailto:test@grove.test",
})
@ActiveProfiles("test")
class PushDeliveryTest {

	@Autowired PushService push;
	@Autowired PushSubscriptionRepository subscriptions;
	@Autowired UserRepository users;
	@MockBean JavaMailSender mailSender;

	private HttpServer server;
	private final Map<String, Map<String, String>> received = new ConcurrentHashMap<>();
	private final List<Integer> bodySizes = new CopyOnWriteArrayList<>();

	@BeforeEach
	void start() throws Exception {
		subscriptions.deleteAll();
		users.deleteAll();
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			received.put(path, Map.of(
				"auth", exchange.getRequestHeaders().getFirst("Authorization"),
				"encoding", exchange.getRequestHeaders().getFirst("Content-Encoding"),
				"ttl", exchange.getRequestHeaders().getFirst("TTL"),
				"urgency", exchange.getRequestHeaders().getFirst("Urgency")));
			bodySizes.add(exchange.getRequestBody().readAllBytes().length);
			exchange.sendResponseHeaders(path.contains("gone") ? 410 : 201, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void sendsEncryptedSignedMessagesAndForgetsDeadDevices() {
		User ava = user("ava@grove.test", true);
		User gone = user("old@grove.test", false);
		device(ava, "/live");
		device(ava, "/gone");
		device(gone, "/disabled");

		push.notify(List.of(ava.getUserId(), gone.getUserId()), "New schedule posted", "The week of Nov 7 is up.", "/schedule");

		await().atMost(Duration.ofSeconds(10)).until(() -> received.containsKey("/live")
			&& subscriptions.findByEndpoint(base() + "/gone").isEmpty());

		Map<String, String> live = received.get("/live");
		assertThat(live.get("encoding")).isEqualTo("aes128gcm");
		assertThat(live.get("ttl")).isEqualTo("86400");
		assertThat(live.get("auth")).startsWith("vapid t=")
			.endsWith(", k=BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
		// Salt, record size, and key, plus the encrypted message: never the plain text size alone.
		assertThat(bodySizes).allMatch(size -> size > 86);
		// A disabled account gets nothing; a working device stays saved.
		assertThat(received).doesNotContainKey("/disabled");
		assertThat(subscriptions.findByEndpoint(base() + "/live")).isPresent();
	}

	@Test
	void aTestReportsWhatEachPushServiceAnswered() {
		User ava = user("ava@grove.test", true);
		device(ava, "/live");
		device(ava, "/gone");

		assertThat(push.test(ava)).extracting(r -> r.status()).containsExactlyInAnyOrder(201, 410);
		assertThat(received.get("/live").get("urgency")).isEqualTo("high");
		assertThat(subscriptions.findByEndpoint(base() + "/gone")).isEmpty();
		assertThat(push.test(user("new@grove.test", true))).isEmpty();
	}

	private String base() {
		return "http://localhost:" + server.getAddress().getPort();
	}

	private void device(User owner, String path) {
		PushSubscription s = new PushSubscription();
		s.setUserId(owner.getUserId());
		s.setEndpoint(base() + path);
		s.setP256dh("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
		s.setAuth("BTBZMqHH6r4Tts7J_aSIgg");
		s.setCreatedAt(LocalDateTime.now());
		subscriptions.save(s);
	}

	private User user(String email, boolean active) {
		User u = new User();
		u.setEmail(email);
		u.setPassword("x");
		u.setRoles(Role.STYLIST);
		u.setActive(active);
		return users.save(u);
	}
}
