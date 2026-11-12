package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.BlackoutDate;

@Repository
public interface BlackoutDateRepository extends JpaRepository<BlackoutDate, LocalDate> {

	List<BlackoutDate> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

	List<BlackoutDate> findByDateGreaterThanEqualOrderByDateAsc(LocalDate from);
}
