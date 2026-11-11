package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService auditService;

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("customers", bankService.getAllCustomers(keyword));
        model.addAttribute("transactions", bankService.getLatestTransactions());
        model.addAttribute("totalBalance", bankService.getTotalBalance());
        model.addAttribute("customerCount", bankService.getCustomerCount());
        model.addAttribute("successCount", bankService.getSuccessCount());
        model.addAttribute("failedCount", bankService.getFailedCount());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new TransferRequest());
        }
        return "bank";
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute TransferRequest transferRequest,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.transferRequest",
                    bindingResult);
            redirectAttributes.addFlashAttribute("transferRequest", transferRequest);
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the transfer form.");
            return "redirect:/";
        }

        try {
            bankService.transfer(transferRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Transfer completed successfully.");
        } catch (Exception ex) {
            // This controller catch runs after BankService's transaction has rolled back.
            String reason = ex.getMessage() == null ? "Transfer failed." : ex.getMessage();
            try {
                auditService.recordFailure(
                        transferRequest.getFromAccount(),
                        transferRequest.getToAccount(),
                        transferRequest.getAmount(),
                        reason
                );
            } catch (Exception auditException) {
                reason += " (The failure audit record could not be saved.)";
            }
            redirectAttributes.addFlashAttribute("errorMessage", reason);
        }
        return "redirect:/";
    }

    @PostMapping("/customers")
    public String addCustomer(@RequestParam String accountNumber,
                              @RequestParam String fullName,
                              @RequestParam String email,
                              @RequestParam BigDecimal openingBalance,
                              RedirectAttributes redirectAttributes) {
        try {
            if (accountNumber == null || accountNumber.isBlank()
                    || fullName == null || fullName.isBlank()
                    || email == null || email.isBlank()
                    || openingBalance == null || openingBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Enter valid customer details and a non-negative opening balance.");
            }
            bankService.createCustomer(accountNumber.trim(), fullName.trim(),
                    email.trim(), openingBalance);
            redirectAttributes.addFlashAttribute("successMessage", "Customer added successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/accounts/{accountNumber}/history")
    public String accountHistory(@PathVariable String accountNumber, Model model) {
        Customer customer = bankService.getCustomer(accountNumber);
        model.addAttribute("customer", customer);
        model.addAttribute("transactions", bankService.getAccountHistory(accountNumber));
        model.addAttribute("totalSent", bankService.getTotalSent(accountNumber));
        return "history";
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public String handleAccountNotFound(AccountNotFoundException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/";
    }
}
