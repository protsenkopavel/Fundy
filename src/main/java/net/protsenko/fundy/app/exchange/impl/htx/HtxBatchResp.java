package net.protsenko.fundy.app.exchange.impl.htx;

import java.util.List;

public record HtxBatchResp(
        String status,
        List<Tick> ticks
) {

    public record Tick(
            String contract_code,
            String close,
            String high,
            String low,
            String vol,
            double[] ask,
            double[] bid
    ) {

        public String contractCode() {
            return contract_code;
        }
    }
}
