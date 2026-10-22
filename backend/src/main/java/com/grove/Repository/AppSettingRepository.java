package com.grove.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.AppSetting;

@Repository
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
