package com.grove.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.EmployeeDetails;
import com.grove.Domain.Dtos.TeamMember;
import com.grove.Domain.Dtos.TeamMemberUpdate;
import com.grove.Domain.LoginDTO;
import com.grove.Domain.Role;
import com.grove.Domain.User;
import com.grove.Repository.UserRepository;
import com.grove.Repository.VideoCompletionRepository;

/**
 * Account creation, credential checks, and the manager's team controls.
 *
 * Sign-up is gated by a shared team code (TEAM_SIGNUP_CODE) so only staff can
 * make accounts; emails listed in ADMIN_EMAILS become managers on sign-up.
 */
@Service
public class UserService {

	private static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository userRepo;
	private final VideoCompletionRepository completionRepo;
	private final PasswordEncoder passwordEncoder;
	private final String signupCode;
	private final Set<String> adminEmails;

	public UserService(UserRepository userRepo, VideoCompletionRepository completionRepo,
			PasswordEncoder passwordEncoder,
			@Value("${app.signup-code:}") String signupCode,
			@Value("${app.admin-emails:}") String adminEmails) {
		this.userRepo = userRepo;
		this.completionRepo = completionRepo;
		this.passwordEncoder = passwordEncoder;
		this.signupCode = signupCode.trim();
		this.adminEmails = Arrays.stream(adminEmails.split(","))
			.map(s -> s.trim().toLowerCase()).filter(s -> !s.isEmpty())
			.collect(Collectors.toSet());
	}

	public boolean isSignupOpen() {
		return !signupCode.isEmpty();
	}

	/** Create an account. 403 on a wrong team code, 409 if the email is already taken. */
	public User register(LoginDTO request) {
		if (!isSignupOpen()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sign-up is closed. Ask your manager for an account.");
		}
		if (request.getTeamCode() == null || !constantTimeEquals(request.getTeamCode().trim(), signupCode)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That team code isn't right. Ask your manager for it.");
		}
		if (request.getEmail() == null || request.getEmail().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
		}
		validatePassword(request.getPassword());
		String email = request.getEmail().trim().toLowerCase();
		if (userRepo.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
		}

		User user = new User();
		user.setEmail(email);
		user.setFirstName(trimOrNull(request.getFirstName()));
		user.setLastName(trimOrNull(request.getLastName()));
		user.setPassword(passwordEncoder.encode(request.getPassword()));
		user.setRoles(adminEmails.contains(email) ? Role.ADMIN : Role.USER);
		user.setCreatedAt(LocalDateTime.now());
		return userRepo.save(user);
	}

	/** Verify credentials and return the User, or 401 on any mismatch or a deactivated account. */
	public User authenticate(LoginDTO request) {
		String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
		User user = userRepo.findByEmail(email)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
		if (request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
		}
		if (!user.isActive()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This account has been disabled. Talk to your manager.");
		}
		return user;
	}

	// ---- Manager controls ----------------------------------------------------

	public List<TeamMember> team() {
		return userRepo.findAll().stream()
			.sorted(Comparator.comparing(User::isActive).reversed()
				.thenComparing(u -> (u.getFirstName() == null ? "" : u.getFirstName()).toLowerCase()))
			.map(this::toTeamMember)
			.toList();
	}

	public TeamMember member(Long userId) {
		return toTeamMember(find(userId));
	}

	/** Disable/re-enable an employee or change their role. A manager can't lock themselves out. */
	public TeamMember update(User actor, Long userId, TeamMemberUpdate change) {
		User user = find(userId);
		boolean self = user.getUserId().equals(actor.getUserId());
		if (change.active() != null) {
			if (self && !change.active()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't disable your own account");
			}
			if (change.active() != user.isActive()) {
				user.setActive(change.active());
				user.setDisabledAt(change.active() ? null : LocalDateTime.now());
			}
		}
		if (change.role() != null) {
			if (self && change.role() != Role.ADMIN) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't remove your own manager access");
			}
			user.setRoles(change.role());
		}
		return toTeamMember(userRepo.save(user));
	}

	/** Update the details a manager keeps on file. Changing the email changes their sign-in. */
	public TeamMember updateDetails(Long userId, EmployeeDetails d) {
		User user = find(userId);
		String email = d.email() == null ? "" : d.email().trim().toLowerCase();
		if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
		}
		if (!email.equals(user.getEmail()) && userRepo.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Another account already uses that email");
		}
		if (d.birthday() != null && d.birthday().isAfter(LocalDate.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Birthday can't be in the future");
		}
		String phone = trimOrNull(d.phone());
		if (phone != null && phone.length() > 30) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number is too long");
		}
		user.setFirstName(trimOrNull(d.firstName()));
		user.setLastName(trimOrNull(d.lastName()));
		user.setEmail(email);
		user.setPhone(phone);
		user.setHireDate(d.hireDate());
		user.setBirthday(d.birthday());
		return toTeamMember(userRepo.save(user));
	}

	/** Set a temporary password for someone who forgot theirs. */
	public void resetPassword(Long userId, String newPassword) {
		validatePassword(newPassword);
		User user = find(userId);
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepo.save(user);
	}

	private User find(Long userId) {
		return userRepo.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such employee"));
	}

	private TeamMember toTeamMember(User u) {
		return new TeamMember(u.getUserId(), u.getFirstName(), u.getLastName(), u.getEmail(),
			u.getPhone(), u.getHireDate(), u.getBirthday(), u.getRoles(), u.isActive(), u.getDisabledAt(),
			completionRepo.countByUserId(u.getUserId()), u.getCreatedAt());
	}

	private static void validatePassword(String password) {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
		}
	}

	private static String trimOrNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static boolean constantTimeEquals(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}
}
