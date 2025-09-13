package net.protsenko.fundy.app.dto.rs;

import net.protsenko.fundy.app.exchange.ExchangeType;

public record WithdrawalDepositStatus(
        ExchangeType exchange,
        String asset,
        DepositWithdrawStatus withdrawStatus,
        DepositWithdrawStatus depositStatus,
        long lastUpdated
) {
    public boolean canWithdraw() {
        return withdrawStatus == DepositWithdrawStatus.ENABLED;
    }

    public boolean canDeposit() {
        return depositStatus == DepositWithdrawStatus.ENABLED;
    }
}