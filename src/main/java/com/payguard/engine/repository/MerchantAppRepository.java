package com.payguard.engine.repository;

import com.payguard.engine.entity.MerchantApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantAppRepository extends JpaRepository<MerchantApp, String> {

    // Dùng cho Interceptor lúc đối tác gọi API vào hệ thống
    Optional<MerchantApp> findByApiKeyAndActiveTrue(String apiKey);
}
