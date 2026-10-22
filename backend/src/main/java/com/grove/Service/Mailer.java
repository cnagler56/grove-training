package com.grove.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text notification emails in the background, so a slow or broken
 * mail server never blocks or fails the action that triggered it. Without
 * MAIL_HOST set, it only logs what it would have sent.
 */
@Service
public class Mailer {

	private final ObjectProvider<JavaMailSender> mailSender;
	private final String mailHost;
	private final String from;
	private final String frontendUrl;

	public Mailer(ObjectProvider<JavaMailSender> mailSender,
			@Value("${spring.mail.host:}") String mailHost,
			@Value("${app.mail.from:}") String from,
			@Value("${app.frontend-url:http://localhost:3001}") String frontendUrl) {
		this.mailSender = mailSender;
		this.mailHost = mailHost.trim();
		this.from = from.trim();
		this.frontendUrl = frontendUrl.replaceAll("/+$", "");
	}

	/** Full link to a page on the site, for email bodies. */
	public String link(String path) {
		return frontendUrl + path;
	}

	public void send(List<String> to, String subject, String body) {
		JavaMailSender sender = mailSender.getIfAvailable();
		if (to.isEmpty() || mailHost.isEmpty() || sender == null) {
			System.out.println("[MAIL] Email is off (no MAIL_HOST); would have sent: " + subject);
			return;
		}
		SimpleMailMessage msg = new SimpleMailMessage();
		if (!from.isEmpty()) msg.setFrom(from);
		msg.setTo(to.toArray(String[]::new));
		msg.setSubject(subject);
		msg.setText(body);
		CompletableFuture.runAsync(() -> {
			try {
				sender.send(msg);
			} catch (Exception e) {
				System.out.println("[MAIL] Couldn't send \"" + subject + "\": " + e.getMessage());
			}
		});
	}
}
