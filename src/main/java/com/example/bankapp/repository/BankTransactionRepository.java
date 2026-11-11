package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    // All transactions, newest first
    @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC")
    List<BankTransaction> findAllLatest();

    // Account history: transactions where the account sent or received money
    @Query("SELECT t FROM BankTransaction t " +
           "WHERE t.fromAccount = :account OR t.toAccount = :account " +
           "ORDER BY t.createdAt DESC")
    List<BankTransaction> findHistory(@Param("account") String account);

    // Filter transactions by status
    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    // Dashboard count for SUCCESS / FAILED
    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    // Assignment extension: transfers above a minimum amount
    @Query("SELECT t FROM BankTransaction t WHERE t.amount >= :minAmount ORDER BY t.createdAt DESC")
    List<BankTransaction> findTransfersAbove(@Param("minAmount") BigDecimal minAmount);

    // Assignment extension: total successfully sent by an account
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BankTransaction t " +
           "WHERE t.fromAccount = :account AND t.status = com.example.bankapp.model.TransactionStatus.SUCCESS")
    BigDecimal getTotalSentByAccount(@Param("account") String account);
}
