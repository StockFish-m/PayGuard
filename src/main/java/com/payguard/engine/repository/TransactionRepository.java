package com.payguard.engine.repository;

import com.payguard.engine.entity.Transaction;
import com.payguard.engine.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findByStatus(TransactionStatus status);

    List<Transaction> findByStatusIn(Collection<TransactionStatus> statuses);
}