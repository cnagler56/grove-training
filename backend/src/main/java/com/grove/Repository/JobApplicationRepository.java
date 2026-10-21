package com.grove.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.ApplicationStatus;
import com.grove.Domain.JobApplication;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

	List<JobApplication> findAllByOrderByCreatedAtDesc();

	long countByStatus(ApplicationStatus status);
}
