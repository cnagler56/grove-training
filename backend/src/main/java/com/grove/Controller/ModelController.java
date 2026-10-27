package com.grove.Controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.HaircutModel;
import com.grove.Repository.HaircutModelRepository;
import com.grove.Service.SessionService;

/** The Models page: people who'll sit for a haircut. Managers and up can list, add, change, and remove them. */
@RestController
public class ModelController {

	/** What the page sends when adding or changing a model. */
	public record ModelRequest(String name, String source, String phone, String notes, LocalDate lastVisit) {}

	private final HaircutModelRepository models;
	private final SessionService sessions;

	public ModelController(HaircutModelRepository models, SessionService sessions) {
		this.models = models;
		this.sessions = sessions;
	}

	/** Everyone, A–Z by name. */
	@GetMapping("/api/admin/models")
	public List<HaircutModel> list(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return models.findAll().stream()
			.sorted(Comparator.comparing((HaircutModel m) -> m.getName().toLowerCase()))
			.toList();
	}

	@PostMapping("/api/admin/models")
	public ResponseEntity<HaircutModel> create(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody ModelRequest body) {
		sessions.requireAdmin(token);
		HaircutModel m = new HaircutModel();
		m.setCreatedAt(LocalDateTime.now());
		apply(m, body);
		return ResponseEntity.status(HttpStatus.CREATED).body(models.save(m));
	}

	@PutMapping("/api/admin/models/{id}")
	public HaircutModel update(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody ModelRequest body) {
		sessions.requireAdmin(token);
		HaircutModel m = find(id);
		apply(m, body);
		return models.save(m);
	}

	@DeleteMapping("/api/admin/models/{id}")
	public ResponseEntity<Void> delete(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		sessions.requireAdmin(token);
		models.delete(find(id));
		return ResponseEntity.noContent().build();
	}

	private HaircutModel find(Long id) {
		return models.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That model isn't on the list anymore"));
	}

	private static void apply(HaircutModel m, ModelRequest body) {
		String name = trim(body == null ? null : body.name());
		if (name == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every model needs a name");
		m.setName(limit(name, 120));
		m.setSource(limit(trim(body.source()), 120));
		m.setPhone(limit(trim(body.phone()), 30));
		m.setNotes(limit(trim(body.notes()), 2000));
		if (body.lastVisit() != null && body.lastVisit().isAfter(LocalDate.now().plusDays(1))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last visit can't be in the future");
		}
		m.setLastVisit(body.lastVisit());
		m.setUpdatedAt(LocalDateTime.now());
	}

	private static String trim(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String limit(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}
