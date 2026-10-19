package com.grove.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import jakarta.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.User;
import com.grove.Domain.UserSession;
import com.grove.Repository.UserRepository;
import com.grove.Repository.UserSessionRepository;

/**
 * Server-side session management. Sign-in creates a row + an opaque token,
 * the token rides in an HttpOnly cookie, and every authenticated request
 * resolves user identity by looking the row up.
 *
 * Sessions auto-expire after {@code SESSION_LIFETIME_DAYS} (default 30) and
 * a daily scheduled job clears the dead ones. A deactivated user's sessions
 * stop resolving immediately.
 */
@Service
public class SessionService {

    public static final String COOKIE_NAME = "grove_session";
    private static final int SESSION_LIFETIME_DAYS = 30;
    private static final int TOKEN_BYTES = 32; // 256 bits → 43-char base64url

    private final UserSessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final SecureRandom random = new SecureRandom();

    public SessionService(UserSessionRepository sessionRepo, UserRepository userRepo) {
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
    }

    /** Mint a new session for the given user and persist it. Returns the raw token. */
    public String createSession(Long userId) {
        UserSession s = new UserSession();
        s.setToken(randomToken());
        s.setUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        s.setCreatedAt(now);
        s.setExpiresAt(now.plusDays(SESSION_LIFETIME_DAYS));
        sessionRepo.save(s);
        return s.getToken();
    }

    /** Resolve a session cookie value back to its active User, or empty if expired/missing. */
    public Optional<User> findUserByToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Optional<UserSession> session = sessionRepo.findByToken(token);
        if (session.isEmpty()) return Optional.empty();
        UserSession s = session.get();
        if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(LocalDateTime.now())) {
            sessionRepo.delete(s);
            return Optional.empty();
        }
        return userRepo.findById(s.getUserId()).filter(User::isActive);
    }

    /** The signed-in user, or 401. */
    public User requireUser(String token) {
        return findUserByToken(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
    }

    /** The signed-in manager, or 401/403. */
    public User requireAdmin(String token) {
        User user = requireUser(token);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Managers only");
        }
        return user;
    }

    /** Best-effort logout. Safe to call with an unknown token. */
    @Transactional
    public void invalidate(String token) {
        if (token == null || token.isBlank()) return;
        sessionRepo.deleteByToken(token);
    }

    /** Daily 3 AM cleanup of expired sessions so the table doesn't grow unbounded. */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Chicago")
    @Transactional
    public void cleanupExpired() {
        int n = sessionRepo.deleteExpiredBefore(LocalDateTime.now());
        if (n > 0) System.out.println("[SESSIONS] cleaned up " + n + " expired sessions");
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
