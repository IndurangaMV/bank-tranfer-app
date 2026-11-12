package com.example.bankapp.service;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.exception.SameAccountException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    public List<Customer> getAllCustomers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return customerRepository.findAllOrdered();
        }
        return customerRepository.searchByName(keyword.trim());
    }

    public List<BankTransaction> getLatestTransactions() {
        return transactionRepository.findAllLatest();
    }

    public long getSuccessCount() {
        return transactionRepository.countByStatus(TransactionStatus.SUCCESS);
    }

    public long getFailedCount() {
        return transactionRepository.countByStatus(TransactionStatus.FAILED);
    }

    public BigDecimal getTotalBalance() {
        BigDecimal total = customerRepository.getTotalBalance();
        return total == null ? BigDecimal.ZERO : total;
    }

    public long getCustomerCount() {
        return customerRepository.countCustomersNative();
    }

    public Customer getCustomer(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account " + accountNumber + " was not found."));
    }

    public List<BankTransaction> getAccountHistory(String accountNumber) {
        getCustomer(accountNumber); // Verify that the account exists.
        return transactionRepository.findHistory(accountNumber);
    }

    public BigDecimal getTotalSent(String accountNumber) {
        return transactionRepository.getTotalSentByAccount(accountNumber);
    }

    @Transactional
    public Customer createCustomer(String accountNumber, String fullName,
                                   String email, BigDecimal openingBalance) {
        if (customerRepository.countAccounts(accountNumber) > 0) {
            throw new IllegalArgumentException("Account number already exists.");
        }
        if (customerRepository.countEmails(email) > 0) {
            throw new IllegalArgumentException("Email address already exists.");
        }
        Customer customer = new Customer(accountNumber, fullName, email, openingBalance);
        return customerRepository.save(customer);
    }

    // The withdrawal, deposit and SUCCESS record are one atomic database transaction.
   @Transactional(rollbackFor = Exception.class)
    //@Transactional(propagation = Propagation.SUPPORTS)
    public BankTransaction transfer(TransferRequest request) {
        String from = request.getFromAccount().trim();
        String to = request.getToAccount().trim();
        BigDecimal amount = request.getAmount();

        if (from.equals(to)) {
            throw new SameAccountException("Sender and receiver must be different accounts.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        }

        // Confirm both accounts exist before attempting updates.
        getCustomer(from);
        getCustomer(to);

        int debited = customerRepository.debit(from, amount);
        if (debited == 0) {
            throw new InsufficientBalanceException("Insufficient balance in the sender account.");
        }

        // Deliberately throw after debit to demonstrate transaction rollback.
        if (request.isSimulateFailure()) {
            throw new RuntimeException("Simulated system failure. All changes were rolled back.");
        }

        int credited = customerRepository.credit(to, amount);
        if (credited == 0) {
            throw new AccountNotFoundException("Receiver account could not be credited.");
        }

        BankTransaction success = new BankTransaction(
                from, to, amount, TransactionStatus.SUCCESS, "Transfer completed successfully."
        );
        return transactionRepository.save(success);
    }
}
