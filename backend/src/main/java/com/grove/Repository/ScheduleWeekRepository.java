package com.grove.Repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.ScheduleWeek;

@Repository
public interface ScheduleWeekRepository extends JpaRepository<ScheduleWeek, LocalDate> {
}
