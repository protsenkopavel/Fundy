import { api } from './client';
import type { Exchange, Token, FundingRow, ArbitrageRow, ArbitrageRequest, FeedbackPayload } from './types';

export const getExchanges = async (): Promise<Exchange[]> =>
    (await api.get('/market/data/exchanges')).data;

export const getTokens = async (): Promise<Token[]> => {
    const def = ["BYBIT","MEXC","GATEIO","KUCOIN","BITGET","COINEX","HTX","OKX","BINGX"];
    const normalize = (list: Token[]) =>
        (list ?? []).map(it => ({
            ...it,
            symbol: it.nativeSymbol ?? `${it.baseAsset ?? ''}${it.quoteAsset ?? ''}`
        }));

    try {
        const r = await api.post('/market/data/instruments', { exchanges: def });
        return normalize(r.data);
    } catch (e: any) {
        // если одна биржа отключена — пробуем без неё (могут быть несколько)
        const msg: string = e?.message ?? '';
        const disabled = [...msg.matchAll(/Биржа отключена:\s*([A-Z0-9_]+)/gi)].map(m => m[1]?.toUpperCase()).filter(Boolean);
        const ex = def.filter(x => !disabled.includes(x));
        if (ex.length) {
            try { return normalize((await api.post('/market/data/instruments', { exchanges: ex })).data); }
            catch { return []; }
        }
        return [];
    }
};

export const postFunding = async (req: {exchanges?: string[]; minFundingRate?: string; timeZone?: string;}): Promise<FundingRow[]> =>
    (await api.post('/market/funding/opportunities', req)).data;

export const postArbitrage = async (req: ArbitrageRequest): Promise<ArbitrageRow[]> =>
    (await api.post('/market/arbitrage/opportunities', req)).data;

export const postFeedback = (p: FeedbackPayload) =>
    api.post('/meta/feedback', p).then(r => r.data);
