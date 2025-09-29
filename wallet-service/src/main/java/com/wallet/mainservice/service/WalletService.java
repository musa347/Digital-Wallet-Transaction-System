package com.wallet.mainservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.mainservice.exception.InsufficientFundsException;
import com.wallet.mainservice.exception.WalletNotFoundException;
import com.wallet.mainservice.kafka.WalletEventProducer;
import com.wallet.mainservice.model.Wallet;
import com.wallet.mainservice.model.WalletTransaction;
import com.wallet.mainservice.repo.WalletRepository;
import com.wallet.mainservice.repo.WalletTransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletEventProducer producer;
    private final ObjectMapper mapper = new ObjectMapper();

    public Wallet createWallet(String userId) {
        Wallet w = new Wallet();
        w.setId(UUID.randomUUID().toString());
        w.setUserId(userId);
        w.setBalance(BigDecimal.ZERO);
        walletRepository.save(w);

        Map<String, Object> evt = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "WALLET_CREATED",
                "walletId", w.getId(),
                "userId", userId,
                "timestamp", Instant.now().toString()
        );

        afterCommit(() -> producer.publish("wallet-events", w.getId(), writeJson(evt)));
        return w;
    }

    @Transactional
    public WalletTransaction fundWallet(String walletId, BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        Wallet w = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found with id: " + walletId));

        w.setBalance(w.getBalance().add(amount));
        walletRepository.save(w);

        WalletTransaction tx = new WalletTransaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setWalletId(w.getId());
        tx.setAmount(amount);
        tx.setType("FUND");
        tx.setStatus("COMPLETED");
        walletTransactionRepository.save(tx);

        Map<String, Object> evt = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "WALLET_FUNDED",
                "walletId", w.getId(),
                "userId", w.getUserId(),
                "amount", amount.toPlainString(),
                "transactionId", tx.getId(),
                "timestamp", Instant.now().toString()
        );

        afterCommit(() -> producer.publish("wallet-events", w.getId(), writeJson(evt)));
        return tx;
    }

    @Transactional
    public void transferWallet(String fromWalletId, String toWalletId, BigDecimal amount) {
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("From wallet and to wallet id are the same");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        String first = fromWalletId.compareTo(toWalletId) <= 0 ? fromWalletId : toWalletId;
        String second = first.equals(fromWalletId) ? toWalletId : fromWalletId;

        Wallet w1 = walletRepository.findByIdForUpdate(first).orElseThrow();
        Wallet w2 = walletRepository.findByIdForUpdate(second).orElseThrow();

        Wallet from = fromWalletId.equals(w1.getId()) ? w1 : w2;
        Wallet to = toWalletId.equals(w1.getId()) ? w1 : w2;

        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in wallet: " + from.getId());
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        walletRepository.saveAll(List.of(from, to));

        WalletTransaction outGoing = createTransaction(from.getId(), amount.negate(), "TRANSFER_OUT", "COMPLETED");
        WalletTransaction inComing = createTransaction(to.getId(), amount, "TRANSFER_IN", "COMPLETED");
        walletTransactionRepository.saveAll(List.of(outGoing, inComing));

        String txId = outGoing.getId();
        Map<String, Object> evt = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "TRANSFER_COMPLETED",
                "transactionId", txId,
                "fromWalletId", from.getId(),
                "toWalletId", to.getId(),
                "amount", amount.toPlainString(),
                "timestamp", Instant.now().toString()
        );

        afterCommit(() -> producer.publish("wallet-events", txId, writeJson(evt)));
    }

    private WalletTransaction createTransaction(String walletId, BigDecimal amount, String type, String status) {
        WalletTransaction t = new WalletTransaction();
        t.setId(UUID.randomUUID().toString());
        t.setWalletId(walletId);
        t.setAmount(amount.abs());
        t.setType(type);
        t.setStatus(status);
        return t;
    }

    private String writeJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", obj, e);
            return "{}"; // fallback
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
