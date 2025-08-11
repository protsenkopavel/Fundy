import axios from 'axios';

export const api = axios.create({ baseURL: '/api', timeout: 30000 });

export const getExchanges = () => api.get('/market/data/exchanges').then(r => r.data);

export const getTokens = async () => {
  const defaultExchanges = ["BYBIT","MEXC","GATEIO","KUCOIN","BITGET","COINEX","HTX","OKX","BINGX"];

  const normalize = (list: any[]) => (list ?? []).map((it: any) => ({
    ...it,
    symbol: it.nativeSymbol ?? ((it.baseAsset ? String(it.baseAsset) : '') + (it.quoteAsset ? String(it.quoteAsset) : ''))
  }));

  try {
    const r = await api.post('/market/data/instruments', { exchanges: defaultExchanges });
    return normalize(r.data);
  } catch (err: any) {
    const msg = err?.response?.data?.message ?? err?.message ?? '';
    const m = typeof msg === 'string' ? msg.match(/Биржа отключена:\s*([A-Z0-9_]+)/i) : null;
    if (m && m[1]) {
      const disabled = m[1].toUpperCase();
      const ex = defaultExchanges.filter(e => e !== disabled);
      if (ex.length) {
        try {
          const r2 = await api.post('/market/data/instruments', { exchanges: ex });
          return normalize(r2.data);
        } catch {
          return [];
        }
      }
    }
    return [];
  }
};

export const postFunding = (req: any) => api.post('/market/funding/opportunities', req).then(r => r.data);

export const postArbitrage = (req: any) => api.post('/market/arbitrage/opportunities', req).then(r => r.data);
