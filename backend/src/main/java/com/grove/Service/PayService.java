package com.grove.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.PayCodeEntry;
import com.grove.Domain.Dtos.PayCodeView;
import com.grove.Domain.Dtos.PayMatrixEntry;
import com.grove.Domain.Dtos.PayRules;
import com.grove.Domain.Dtos.PayRulesUpload;
import com.grove.Domain.PayCode;
import com.grove.Domain.PayMatrixRow;
import com.grove.Domain.WeeklyStat;
import com.grove.Repository.PayCodeRepository;
import com.grove.Repository.PayMatrixRepository;

/**
 * Weekly hourly pay, figured exactly like the payroll skill (calculate_payroll.py):
 * <ul>
 * <li>sales = after-discount service sales + 20% of the discount amount</li>
 * <li>PPH = sales ÷ floor hours</li>
 * <li>rate = the matrix rate for the highest step at or below PPH ($16 if none),
 *     plus the person's code premium, but never under $16 ($13 for a negative premium)</li>
 * <li>a flat rate in their code replaces all of that</li>
 * </ul>
 * The matrix and codes come from the skill's files, loaded by the owner and
 * kept only in the database (the code repository is public).
 */
@Service
public class PayService {

	static final BigDecimal BASE_RATE = new BigDecimal("16.00");
	static final BigDecimal MIN_RATE_WITH_DEDUCTION = new BigDecimal("13.00");
	static final BigDecimal DISCOUNT_SHARE = new BigDecimal("0.2");

	private final PayMatrixRepository matrix;
	private final PayCodeRepository codes;

	public PayService(PayMatrixRepository matrix, PayCodeRepository codes) {
		this.matrix = matrix;
		this.codes = codes;
	}

	/** (net + (gross − net) × 0.2) ÷ floor hours, to the cent for showing; 0 with no floor hours. */
	public static BigDecimal pph(WeeklyStat s) {
		return exactPph(s).setScale(2, RoundingMode.HALF_UP);
	}

	/** PPH unrounded, for the matrix lookup: payroll compares the exact figure (38.996 is still under 39). */
	static BigDecimal exactPph(WeeklyStat s) {
		BigDecimal hours = orZero(s.getFloorHours());
		if (hours.signum() <= 0) return BigDecimal.ZERO;
		BigDecimal net = orZero(s.getNetServiceSales());
		BigDecimal gross = orZero(s.getServiceSales());
		BigDecimal sales = net.add(gross.subtract(net).multiply(DISCOUNT_SHARE));
		return sales.divide(hours, 10, RoundingMode.HALF_UP);
	}

	/** Pay rates for many weeks of one person: a lookup that reads the tables once. */
	public Function<WeeklyStat, BigDecimal> rates(String suName) {
		List<PayMatrixRow> steps = sortedMatrix();
		if (steps.isEmpty()) return s -> null;
		PayCode code = suName == null ? null : codes.findAll().stream()
			.filter(c -> c.getSuName().equalsIgnoreCase(suName)).findFirst().orElse(null);
		return s -> rate(exactPph(s), code, steps);
	}

	static BigDecimal rate(BigDecimal pph, PayCode code, List<PayMatrixRow> steps) {
		if (code != null && code.getFlatRate() != null) return code.getFlatRate().setScale(2, RoundingMode.HALF_UP);
		BigDecimal base = BASE_RATE;
		for (PayMatrixRow step : steps) {
			if (pph.compareTo(step.getPph()) >= 0) base = step.getRate();
			else break;
		}
		BigDecimal premium = code == null || code.getPremium() == null ? BigDecimal.ZERO : code.getPremium();
		BigDecimal min = premium.signum() < 0 ? MIN_RATE_WITH_DEDUCTION : BASE_RATE;
		return base.add(premium).max(min).setScale(2, RoundingMode.HALF_UP);
	}

	/** Replace the matrix and codes with the skill's files. */
	@Transactional
	public PayRules load(PayRulesUpload upload) {
		if (upload == null || upload.matrix() == null || upload.matrix().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The matrix file has no rows");
		}
		if (upload.codes() == null || upload.codes().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The employee codes file has no one in it");
		}
		for (PayMatrixEntry e : upload.matrix()) {
			if (e.pph() == null || e.rate() == null || e.pph().signum() < 0 || e.rate().signum() <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every matrix row needs a PPH and a rate");
			}
		}
		Map<BigDecimal, PayMatrixEntry> unique = upload.matrix().stream()
			.collect(Collectors.toMap(e -> e.pph().setScale(2, RoundingMode.HALF_UP), Function.identity(), (a, b) -> b));
		matrix.deleteAllInBatch();
		matrix.saveAll(unique.entrySet().stream().map(e -> new PayMatrixRow(e.getKey(), e.getValue().rate())).toList());

		codes.deleteAllInBatch();
		codes.saveAll(upload.codes().entrySet().stream()
			.map(e -> {
				PayCodeEntry c = e.getValue();
				return new PayCode(salonUltimateName(e.getKey()), c.code(), c.premium(), c.flatRate());
			})
			.collect(Collectors.toMap(c -> c.getSuName().toLowerCase(), Function.identity(), (a, b) -> b))
			.values());
		return rules();
	}

	public PayRules rules() {
		return new PayRules(
			sortedMatrix().stream().map(r -> new PayMatrixEntry(r.getPph(), r.getRate())).toList(),
			codes.findAll().stream()
				.sorted(Comparator.comparing(c -> c.getSuName().toLowerCase()))
				.map(c -> new PayCodeView(c.getSuName(), c.getCode(), c.getPremium(), c.getFlatRate()))
				.toList());
	}

	/** "De La Cruz, Mia" (the codes file) → "Mia De La Cruz" (Salon Ultimate). */
	static String salonUltimateName(String fileName) {
		int comma = fileName.indexOf(',');
		if (comma < 0) return fileName.trim();
		return (fileName.substring(comma + 1).trim() + " " + fileName.substring(0, comma).trim()).trim();
	}

	private List<PayMatrixRow> sortedMatrix() {
		return matrix.findAll().stream().sorted(Comparator.comparing(PayMatrixRow::getPph)).toList();
	}

	private static BigDecimal orZero(BigDecimal n) {
		return n == null ? BigDecimal.ZERO : n;
	}
}
