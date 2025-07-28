package net.protsenko.fundy.app.exchange.impl.coinex;

public record CoinexContractItem(
        String name,
        int type,
        String stock,
        String money,
        boolean available,
        Funding funding
) {
    public record Funding(
            int interval,
            String max,
            String min
    ) {
    }
}
