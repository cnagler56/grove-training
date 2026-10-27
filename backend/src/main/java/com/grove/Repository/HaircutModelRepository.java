package com.grove.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.HaircutModel;

@Repository
public interface HaircutModelRepository extends JpaRepository<HaircutModel, Long> {
}
