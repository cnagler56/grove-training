package com.grove.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What's booked in Salon Ultimate's appointment book for a day: the services by
 * category, how many guests, and the booked total (the prices on the book), for the
 * goals card. Read only, and kept for a few minutes so page loads don't each pull it.
 */
@Service
public class AppointmentBookService {

	private static final Logger log = LoggerFactory.getLogger(AppointmentBookService.class);
	/** How long a pulled day is reused. */
	static final Duration FRESH = Duration.ofMinutes(10);
	/** How far ahead anyone can look. */
	static final int MAX_DAYS_AHEAD = 14;

	private final SalonUltimateClient salonUltimate;
	private final ObjectMapper json = new ObjectMapper();
	private final ZoneId zone;
	private final Map<LocalDate, Cached> cache = new ConcurrentHashMap<>();

	public AppointmentBookService(SalonUltimateClient salonUltimate, @Value("${app.time-zone:America/Chicago}") String zone) {
		this.salonUltimate = salonUltimate;
		this.zone = ZoneId.of(zone);
	}

	/**
	 * One day's book. {@code problem} says why it couldn't be read (then the numbers are 0).
	 * {@code guests}: separate visits (a guest booked for a cut and a color counts once).
	 */
	public record Booked(LocalDate date, String problem, BigDecimal projected, int guests, int services, int haircuts,
		int colors, int treatments, int waxes) {
		static Booked failed(LocalDate date, String problem) {
			return new Booked(date, problem, BigDecimal.ZERO, 0, 0, 0, 0, 0, 0);
		}
	}

	private record Cached(Booked booked, Instant at) {}

	public Booked booked(LocalDate date) {
		LocalDate today = LocalDate.now(zone);
		if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bookings can be seen from today up to two weeks ahead");
		}
		if (!salonUltimate.isConfigured()) return Booked.failed(date, "Salon Ultimate sign-in isn't set up on the server.");
		Cached c = cache.get(date);
		if (c != null && c.at().isAfter(Instant.now().minus(FRESH))) return c.booked();
		Booked b = pull(date);
		// Keep a good answer; let a failure be tried again on the next look.
		if (b.problem() == null) cache.put(date, new Cached(b, Instant.now()));
		else if (c != null) return c.booked();
		return b;
	}

	private Booked pull(LocalDate date) {
		try {
			return parse(date, salonUltimate.appointmentBook(date));
		} catch (SalonUltimateClient.LoginFailed e) {
			return Booked.failed(date, "Salon Ultimate didn't accept the sign-in.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Booked.failed(date, "The pull was interrupted.");
		} catch (Exception e) {
			log.warn("Appointment book for {} failed", date, e);
			return Booked.failed(date, "Couldn't read the appointment book.");
		}
	}

	/** Totals from the appointment book's JSON. */
	Booked parse(LocalDate date, String body) throws java.io.IOException {
		JsonNode root = json.readTree(body);
		JsonNode book = root.path("response");
		JsonNode list = book.path("appointments");
		if (!list.isArray()) return Booked.failed(date, "The appointment book came back in a shape the site doesn't know.");
		BigDecimal total = BigDecimal.ZERO;
		Set<String> visits = new HashSet<>();
		int services = 0, haircuts = 0, colors = 0, treatments = 0, waxes = 0;
		for (JsonNode a : list) {
			services++;
			total = total.add(a.path("price").decimalValue());
			long group = a.path("appointmentGroupId").asLong(0);
			visits.add(group != 0 ? "g" + group : "a" + a.path("id").asText(String.valueOf(services)));
			switch (a.path("service").path("myServiceCategoryName").asText("").trim().toLowerCase()) {
				case "haircut" -> haircuts++;
				case "color" -> colors++;
				case "treatment" -> treatments++;
				case "waxing" -> waxes++;
				default -> { }
			}
		}
		// The book's own total when it has one; it matches the sum of prices.
		JsonNode stated = book.path("appointmentBooksStats").path("totalSales");
		if (stated.isNumber()) total = stated.decimalValue();
		return new Booked(date, null, total.setScale(0, RoundingMode.HALF_UP), visits.size(), services, haircuts, colors,
			treatments, waxes);
	}
}
