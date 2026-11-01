package com.grove.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.grove.Domain.PushSubscription;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

	List<PushSubscription> findByUserIdIn(Collection<Long> userIds);

	Optional<PushSubscription> findByEndpoint(String endpoint);

	@Modifying
	@Transactional
	@Query("delete from PushSubscription s where s.endpoint = :endpoint")
	void deleteByEndpoint(@Param("endpoint") String endpoint);

	@Modifying
	@Query("delete from PushSubscription s where s.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
