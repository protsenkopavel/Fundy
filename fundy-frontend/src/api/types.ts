// src/api/types.ts
export type Exchange = { name: string; code?: string };
export type Token = { symbol: string; baseAsset?: string; quoteAsset?: string; nativeSymbol?: string };

export type FundingRow = {
    exchange: string;
    symbol?: string; nativeSymbol?: string;
    fundingRate?: number;
    nextFundingTs?: number;
};

export type ArbitrageRow = {
    token: string;
    prices: Record<string, number>;
    fundingRates: Record<string, number>;
    nextFundingTs: Record<string, number>;
    priceSpread?: number;
    fundingSpread?: number;
    decision?: { longEx?: string; shortEx?: string };
};

export type ArbitrageRequest = {
    exchanges?: string[];
    minFundingRate?: number;   // 0.001 == 0.1%
    minPerpetualPrice?: number;
    timeZone?: string;
    sameAccrualTime?: boolean;
};

export type DepositWithdrawStatus = 'ENABLED' | 'DISABLED' | 'UNKNOWN';

export type SpotArbitrageRow = {
    instrument: { base: string; quote: string; canonicalKey: string };
    token: string;
    coin: string;
    pair: string;
    buyExchange: string;
    withdrawalStatus: string;
    sellExchange: string;
    depositStatus: string;
    priceSpread: number;
    buyPrice: number;
    sellPrice: number;
    buyVolume24h: number;
    sellVolume24h: number;
    links: Record<string, string>;
};

export type SpotArbitrageRequest = {
    exchanges?: string[];
    minSpread?: number;
    maxSpread?: number;
};

export type SpotFuturesArbitrageRow = {
    instrument: { base: string; quote: string; canonicalKey: string };
    token: string;
    coin: string;
    pair: string;
    buyExchange: string;
    shortExchange: string;
    buyPrice: number;
    shortPrice: number;
    fundingRate: number;
    nextFundingTs?: number;
    priceSpread: number;
    links: Record<string, string>;
};

export type SpotFuturesArbitrageRequest = {
    exchanges?: string[];
    minSpread?: number;
    maxSpread?: number;
};

export type FeedbackPayload = {
    type: 'bug' | 'idea' | 'question';
    severity?: 'low' | 'normal' | 'high' | 'critical';
    message: string;
    email?: string;
    page?: string;
    extra?: Record<string, any>;
};