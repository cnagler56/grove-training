package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.ClosedDay;

@Repository
public interface ClosedDayRepository extends JpaRepository<ClosedDay, LocalDate> {

	List<ClosedDay> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
