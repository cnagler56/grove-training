package com.grove.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TodoAssignment;

@Repository
public interface TodoAssignmentRepository extends JpaRepository<TodoAssignment, Long> {

	List<TodoAssignment> findByAssigneeIdOrderByAssignedAtAsc(Long assigneeId);

	List<TodoAssignment> findByAssignedByOrderByAssignedAtDesc(Long assignedBy);

	boolean existsByAssigneeIdAndTextAndCompletedAtIsNull(Long assigneeId, String text);

	@Modifying
	@Query("delete from TodoAssignment t where t.assigneeId = :userId")
	void deleteByAssigneeId(@Param("userId") Long userId);
}
