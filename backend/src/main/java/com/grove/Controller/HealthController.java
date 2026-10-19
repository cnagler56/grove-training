package com.grove.Controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness probe — used by container health checks. */
@RestController
public class HealthController {

	@GetMapping("/api/health")
	public Map<String, Object> health() {
		return Map.of(
			"status", "ok",
			"service", "grove-server",
			"time", Instant.now().toString()
		);
	}
}
