package com.grove.Controller;

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

import com.grove.Domain.BusinessContact;
import com.grove.Repository.BusinessContactRepository;
import com.grove.Service.SessionService;

/** The Contacts page: numbers for vendors, the landlord, and so on. Managers and up can list, add, change, and remove them. */
@RestController
public class ContactController {

	/** What the page sends when adding or changing a contact. */
	public record ContactRequest(String name, String phone, String type) {}

	private final BusinessContactRepository contacts;
	private final SessionService sessions;

	public ContactController(BusinessContactRepository contacts, SessionService sessions) {
		this.contacts = contacts;
		this.sessions = sessions;
	}

	/** Everyone, A–Z by name. */
	@GetMapping("/api/admin/contacts")
	public List<BusinessContact> list(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireAdmin(token);
		return contacts.findAll().stream()
			.sorted(Comparator.comparing((BusinessContact c) -> c.getName().toLowerCase()))
			.toList();
	}

	@PostMapping("/api/admin/contacts")
	public ResponseEntity<BusinessContact> create(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody ContactRequest body) {
		sessions.requireAdmin(token);
		BusinessContact c = new BusinessContact();
		c.setCreatedAt(LocalDateTime.now());
		apply(c, body);
		return ResponseEntity.status(HttpStatus.CREATED).body(contacts.save(c));
	}

	@PutMapping("/api/admin/contacts/{id}")
	public BusinessContact update(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody ContactRequest body) {
		sessions.requireAdmin(token);
		BusinessContact c = find(id);
		apply(c, body);
		return contacts.save(c);
	}

	@DeleteMapping("/api/admin/contacts/{id}")
	public ResponseEntity<Void> delete(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		sessions.requireAdmin(token);
		contacts.delete(find(id));
		return ResponseEntity.noContent().build();
	}

	private BusinessContact find(Long id) {
		return contacts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That contact isn't on the list anymore"));
	}

	private static void apply(BusinessContact c, ContactRequest body) {
		String name = trim(body == null ? null : body.name());
		if (name == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every contact needs a name");
		c.setName(limit(name, 120));
		c.setPhone(phone(body.phone()));
		c.setType(limit(trim(body.type()), 120));
		c.setUpdatedAt(LocalDateTime.now());
	}

	/** A US number as 555-555-0100, or 400. A leading country code 1 is dropped. */
	private static String phone(String raw) {
		String d = raw == null ? "" : raw.replaceAll("\\D", "");
		if (d.length() == 11 && d.startsWith("1")) d = d.substring(1);
		if (d.length() != 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a 10-digit phone number");
		return d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6);
	}

	private static String trim(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String limit(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}
