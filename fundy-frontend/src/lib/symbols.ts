// src/lib/symbols.ts
export const KNOWN_QUOTES = ['USDT','USDC','USD','USDE','FDUSD','TUSD','DAI'] as const;
const DERIV_SUFFIXES = ['SWAP','PERP','USDTM','USDM','UMCBL','CMCBL','DMCBL'];
const KUCOIN_SUFFIX = 'M';

export function toCanonical(raw: string): string {
    if (!raw) return '';
    let s = String(raw).toUpperCase().replace(/[-_/]/g, '');
    for (const suf of DERIV_SUFFIXES) {
        if (s.endsWith(suf)) { s = s.slice(0, -suf.length); break; }
    }
    if (s.endsWith(KUCOIN_SUFFIX) && s.length > 1) s = s.slice(0, -1);
    const sorted = [...KNOWN_QUOTES].sort((a,b)=>b.length-a.length);
    for (const q of sorted) if (s.endsWith(q)) return `${s.slice(0, s.length - q.length)}/${q}`;
    return `${s}/USDT`;
}

export const labelFromCanonical = (canon: string) => canon.replace('/', '').toLowerCase();

export const fmtPct = (v?: number | string | null) => {
    const n = v == null ? NaN : Number(v);
    return Number.isNaN(n) ? '—' : `${(n * 100).toFixed(4)}%`;
};
export const pctColor = (v?: number | string | null) => {
    const n = v == null ? NaN : Number(v);
    if (Number.isNaN(n)) return '#667085';
    if (n > 0) return '#1a7f37';
    if (n < 0) return '#b42318';
    return '#667085';
};
export const fmtPrice = (v?: number | string | null, digits = 3) => {
    const n = v == null ? NaN : Number(v);
    return Number.isNaN(n) ? '—' : n.toFixed(digits);
};
export const fmtTs = (ts?: number | string | null, timeZone?: string) => {
    const n = ts == null ? NaN : Number(ts);
    if (!n || Number.isNaN(n)) return '—';
    try {
        const fmt = new Intl.DateTimeFormat(undefined, {
            timeZone: timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone,
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
        });
        return fmt.format(new Date(n));
    } catch { return new Date(n).toLocaleString(); }
};
