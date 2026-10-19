package com.grove.config;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Global CORS as a servlet filter.
 *
 * Per-controller {@code @CrossOrigin} only adds headers on the handler dispatch,
 * so error responses (404/401/403/500) — which Spring forwards internally to
 * /error — come back without a CORS header and the browser reports a misleading
 * "No 'Access-Control-Allow-Origin'" failure instead of the real status.
 *
 * A {@link CorsFilter} registered for REQUEST + ERROR + ASYNC dispatches applies
 * the headers to every response, including those error dispatches. Credentials
 * are allowed so the session cookie rides cross-origin (Next.js :3001 → here).
 */
@Configuration
public class CorsConfig {

	/**
	 * Comma-separated list of allowed browser origins. Defaults to local dev; set
	 * CORS_ALLOWED_ORIGINS in prod to your deployed frontend URL(s), e.g.
	 * "https://your-frontend.up.railway.app". Credentials are allowed, so origins
	 * must be explicit ("*" is not permitted with credentials).
	 */
	@Value("${CORS_ALLOWED_ORIGINS:http://localhost:3001}")
	private String allowedOrigins;

	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilter() {
		List<String> origins = Arrays.stream(allowedOrigins.split(","))
			.map(String::trim).filter(s -> !s.isEmpty()).toList();

		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);

		FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
		bean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC);
		return bean;
	}
}
