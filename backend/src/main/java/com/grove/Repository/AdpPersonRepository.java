package com.grove.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.AdpPerson;

@Repository
public interface AdpPersonRepository extends JpaRepository<AdpPerson, String> {
}
