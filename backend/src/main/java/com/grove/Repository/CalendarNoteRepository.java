package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.CalendarNote;

@Repository
public interface CalendarNoteRepository extends JpaRepository<CalendarNote, LocalDate> {

	List<CalendarNote> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
