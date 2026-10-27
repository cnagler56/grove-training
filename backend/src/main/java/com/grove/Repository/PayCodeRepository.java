package com.grove.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.PayCode;

@Repository
public interface PayCodeRepository extends JpaRepository<PayCode, String> {
}
