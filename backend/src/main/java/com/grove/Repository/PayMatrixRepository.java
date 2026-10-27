package com.grove.Repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.PayMatrixRow;

@Repository
public interface PayMatrixRepository extends JpaRepository<PayMatrixRow, BigDecimal> {
}
