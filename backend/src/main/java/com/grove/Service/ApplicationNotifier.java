package com.grove.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.grove.Domain.JobApplication;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.UserRepository;

/** Emails every active owner (and only owners) when a job application comes in. */
@Service
public class ApplicationNotifier {

	private final Mailer mailer;
	private final UserRepository users;

	public ApplicationNotifier(Mailer mailer, UserRepository users) {
		this.mailer = mailer;
		this.users = users;
	}

	public void newApplication(JobApplication a) {
		List<String> owners = users.findAll().stream()
			.filter(u -> u.isActive() && u.getRoles() == Role.OWNER)
			.map(User::getEmail)
			.toList();
		String who = a.getFirstName() + " " + a.getLastName();
		mailer.send(owners, "New job application: " + who + " (" + a.getPosition() + ")", String.join("\n",
			who + " applied for " + a.getPosition() + " at Grove Salons.",
			"",
			"Phone: " + a.getPhone(),
			"Email: " + a.getEmail(),
			"",
			"See the full application:",
			mailer.link("/admin/applicants/" + a.getId()),
			"",
			"You're getting this because you're an owner on the Grove Salons site."));
	}
}
