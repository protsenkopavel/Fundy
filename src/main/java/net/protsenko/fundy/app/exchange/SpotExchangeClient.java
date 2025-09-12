package net.protsenko.fundy.app.exchange;

import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.WithdrawalDepositStatus;

import java.util.List;

public interface SpotExchangeClient {

    List<InstrumentData> getSpotInstruments();

    List<TickerData> getSpotTickers(List<InstrumentData> instruments);

    ExchangeType getExchangeType();

    Boolean isEnabled();

    WithdrawalDepositStatus getWithdrawalDepositStatus(String asset);
}