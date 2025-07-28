package net.protsenko.fundy.app.exchange.impl.okx;

import java.util.List;

public record OkxResponse<T>(
        String code,
        String msg,
        List<T> data
) {
}
