package com.wallet.mainservice.controller;

import com.wallet.mainservice.exception.InsufficientFundsException;
import com.wallet.mainservice.model.Wallet;
import com.wallet.mainservice.model.WalletTransaction;
import com.wallet.mainservice.repo.WalletRepository;
import com.wallet.mainservice.repo.WalletTransactionRepository;
import com.wallet.mainservice.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallets")
@Validated
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * Create a new wallet for a user
     */
    @PostMapping
    public ResponseEntity<Wallet> createWallet(@RequestParam @NotBlank(message = "User ID is required") String userId) {
        Wallet wallet = walletService.createWallet(userId);
        return ResponseEntity.ok(wallet);
    }

    /**
     * Fund a wallet by ID
     */
    @PostMapping("/{walletId}/fund")
    public ResponseEntity<WalletTransaction> fundWallet(
            @PathVariable String walletId,
            @RequestParam BigDecimal amount
    ) {
        WalletTransaction tx = walletService.fundWallet(walletId, amount);
        return ResponseEntity.ok(tx);
    }

    /**
     * Transfer funds between wallets
     */
    @PostMapping("/transfer")
    public ResponseEntity<Map<String, String>> transferWallet(
            @RequestParam String fromWalletId,
            @RequestParam String toWalletId,
            @RequestParam BigDecimal amount
    ) {
        walletService.transferWallet(fromWalletId, toWalletId, amount);
        return ResponseEntity.ok(Map.of(
                "message", "Transfer completed successfully",
                "fromWalletId", fromWalletId,
                "toWalletId", toWalletId,
                "amount", amount.toPlainString()
        ));
    }

    /**
     * Get wallet details by ID
     */
    @GetMapping("/{walletId}")
    public ResponseEntity<Wallet> getWallet(@PathVariable String walletId) {
        return walletRepository.findById(walletId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get transaction history for a wallet
     */
    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<List<WalletTransaction>> getTransactions(@PathVariable String walletId) {
        List<WalletTransaction> txs = walletTransactionRepository.findAllByWalletId(walletId);
        return ResponseEntity.ok(txs);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientFunds(InsufficientFundsException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Insufficient Funds",
                "message", e.getMessage()
        ));
    }
}
