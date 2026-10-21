package com.grove.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.grove.Domain.JobApplication;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.UserRepository;

/**
 * Emails every active owner (and only owners) when a job application comes in.
 * Sent in the background so a slow or broken mail server never blocks or
 * fails the applicant's submission. Without MAIL_HOST it just logs.
 */
@Service
public class ApplicationNotifier {

	private final ObjectProvider<JavaMailSender> mailSender;
	private final UserRepository users;
	private final String mailHost;
	private final String from;
	private final String frontendUrl;

	public ApplicationNotifier(ObjectProvider<JavaMailSender> mailSender, UserRepository users,
			@Value("${spring.mail.host:}") String mailHost,
			@Value("${app.mail.from:}") String from,
			@Value("${app.frontend-url:http://localhost:3001}") String frontendUrl) {
		this.mailSender = mailSender;
		this.users = users;
		this.mailHost = mailHost.trim();
		this.from = from.trim();
		this.frontendUrl = frontendUrl.replaceAll("/+$", "");
	}

	public void newApplication(JobApplication a) {
		List<String> owners = users.findAll().stream()
			.filter(u -> u.isActive() && u.getRoles() == Role.OWNER)
			.map(User::getEmail)
			.toList();
		String who = a.getFirstName() + " " + a.getLastName();
		JavaMailSender sender = mailSender.getIfAvailable();
		if (owners.isEmpty() || mailHost.isEmpty() || sender == null) {
			System.out.println("[APPLY] New application from " + who + " (" + a.getPosition() + "). Email notices are off (no MAIL_HOST).");
			return;
		}

		SimpleMailMessage msg = new SimpleMailMessage();
		if (!from.isEmpty()) msg.setFrom(from);
		msg.setTo(owners.toArray(String[]::new));
		msg.setSubject("New job application: " + who + " (" + a.getPosition() + ")");
		msg.setText(String.join("\n",
			who + " applied for " + a.getPosition() + " at Grove Salons.",
			"",
			"Phone: " + a.getPhone(),
			"Email: " + a.getEmail(),
			"",
			"See the full application:",
			frontendUrl + "/admin/applicants/" + a.getId(),
			"",
			"You're getting this because you're an owner on the Grove Salons training site."));

		CompletableFuture.runAsync(() -> {
			try {
				sender.send(msg);
			} catch (Exception e) {
				System.out.println("[APPLY] Couldn't email owners about " + who + ": " + e.getMessage());
			}
		});
	}
}
