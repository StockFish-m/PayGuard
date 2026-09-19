package com.payguard.engine.repository;

import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findByStatus(TransactionStatus status);

    List<Transaction> findByStatusIn(Collection<TransactionStatus> statuses);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}