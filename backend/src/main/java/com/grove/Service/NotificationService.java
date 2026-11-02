package com.grove.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.User;
import com.grove.Domain.UserNotification;
import com.grove.Repository.UserNotificationRepository;

/**
 * The Notifications list: a copy of everything {@link PushService#notify} sends, so
 * people can see what they were told when they open the app normally. Opening one,
 * or the page it points to, marks it read. Kept for {@link #KEEP_DAYS} days.
 */
@Service
public class NotificationService {

	/** The list shows this many days back. */
	static final int SHOWN_DAYS = 30;
	/** Older ones are deleted. */
	static final int KEEP_DAYS = 60;
	/** Where the app opens. */
	private static final String LANDING_PAGE = "/home";
	private static final int MAX_TITLE = 200;
	private static final int MAX_BODY = 500;

	public record NotificationView(Long id, String title, String body, String url, LocalDateTime createdAt, boolean read) {}

	public record Inbox(long unread, List<NotificationView> notifications) {}

	private final UserNotificationRepository notifications;
	private final ZoneId zone;

	public NotificationService(UserNotificationRepository notifications, @Value("${app.time-zone:America/Chicago}") String zone) {
		this.notifications = notifications;
		this.zone = ZoneId.of(zone);
	}

	/** Save one for each of these people. */
	public void save(Collection<Long> userIds, String title, String body, String url) {
		LocalDateTime now = LocalDateTime.now(zone);
		String cleanTitle = limit(title == null || title.isBlank() ? "Grove Salons" : title, MAX_TITLE);
		String cleanBody = body == null || body.isBlank() ? null : limit(body, MAX_BODY);
		String page = url == null || !url.startsWith("/") ? "/home" : limit(url, 200);
		notifications.saveAll(userIds.stream().map(id -> {
			UserNotification n = new UserNotification();
			n.setUserId(id);
			n.setTitle(cleanTitle);
			n.setBody(cleanBody);
			n.setUrl(page);
			n.setCreatedAt(now);
			return n;
		}).toList());
	}

	/** The last {@link #SHOWN_DAYS} days, newest first, and how many are unread. */
	public Inbox inbox(User viewer) {
		LocalDateTime since = since();
		return new Inbox(unread(viewer),
			notifications.findTop50ByUserIdAndCreatedAtAfterOrderByCreatedAtDescIdDesc(viewer.getUserId(), since).stream()
				.map(n -> new NotificationView(n.getId(), n.getTitle(), n.getBody(), n.getUrl(), n.getCreatedAt(), n.getReadAt() != null))
				.toList());
	}

	public long unread(User viewer) {
		return notifications.countByUserIdAndReadAtIsNullAndCreatedAtAfter(viewer.getUserId(), since());
	}

	/** Mark one of your own read. */
	public void read(User viewer, Long id) {
		UserNotification n = notifications.findById(id)
			.filter(x -> x.getUserId().equals(viewer.getUserId()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
		if (n.getReadAt() == null) {
			n.setReadAt(LocalDateTime.now(zone));
			notifications.save(n);
		}
	}

	@Transactional
	public void readAll(User viewer) {
		notifications.markAllRead(viewer.getUserId(), LocalDateTime.now(zone));
	}

	/**
	 * You opened this page, so whatever pointed you to it has been seen. Returns how many
	 * are still unread. Not Home: the app opens there, so that alone doesn't mean you saw
	 * the team board posts that point to it.
	 */
	@Transactional
	public long seen(User viewer, String path) {
		if (path != null && path.startsWith("/") && !path.equals(LANDING_PAGE)) {
			notifications.markReadFor(viewer.getUserId(), path, LocalDateTime.now(zone));
		}
		return unread(viewer);
	}

	@Scheduled(cron = "0 30 3 * * *", zone = "${app.time-zone:America/Chicago}")
	@Transactional
	public void cleanup() {
		notifications.deleteOlderThan(LocalDateTime.now(zone).minusDays(KEEP_DAYS));
	}

	private LocalDateTime since() {
		return LocalDateTime.now(zone).minusDays(SHOWN_DAYS);
	}

	private static String limit(String s, int max) {
		String t = s.trim();
		return t.length() <= max ? t : t.substring(0, max);
	}
}
