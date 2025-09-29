package com.wallet.mainservice.repo;

import com.wallet.mainservice.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    List<WalletTransaction> findAllByWalletId(String walletId);
}
