package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.exchange.ExchangeType;

public record WithdrawalDepositStatus(
        ExchangeType exchange,
        String asset,
        boolean canWithdraw,
        boolean canDeposit,
        long lastUpdated
) {
}