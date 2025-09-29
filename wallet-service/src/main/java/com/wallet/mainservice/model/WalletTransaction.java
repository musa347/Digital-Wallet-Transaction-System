package com.wallet.mainservice.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    private String id;

    private String walletId;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    private String type;

    private String status;

    @CreationTimestamp
    private Instant createdAt;
}
