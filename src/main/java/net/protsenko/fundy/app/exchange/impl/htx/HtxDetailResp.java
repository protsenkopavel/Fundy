package net.protsenko.fundy.app.exchange.impl.htx;

public record HtxDetailResp(
        String ch,
        String status,
        long ts,
        Tick tick
) {
    public record Tick(
            double open,
            double close,
            double high,
            double low,
            double amount,
            double vol,
            double count
    ) {}
}
