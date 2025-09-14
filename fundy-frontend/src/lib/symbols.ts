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
    return Number.isNaN(n) ? '—' : `${(n * 100).toFixed(2)}%`;
};
export const pctColor = (v?: number | string | null) => {
    const n = v == null ? NaN : Number(v);
    if (Number.isNaN(n)) return '#667085';
    if (n > 0) return '#1a7f37';
    if (n < 0) return '#b42318';
    return '#667085';
};
export const fmtPrice = (v?: number | string | null) => {
    const n = v == null ? NaN : Number(v);
    if (Number.isNaN(n)) return '—';

    // Определяем количество знаков в зависимости от величины числа
    let precision = 6;
    if (Math.abs(n) >= 1) precision = 4;
    else if (Math.abs(n) >= 0.1) precision = 5;
    else if (Math.abs(n) >= 0.01) precision = 6;
    else if (Math.abs(n) >= 0.001) precision = 7;
    else precision = 8;

    // Форматируем и удаляем trailing zeros
    const formatted = n.toFixed(precision);
    return formatted.replace(/\.?0+$/, '');
};
export const fmtTs = (ts?: number | string | null, timeZone?: string) => {
    const n = ts == null ? NaN : Number(ts);
    if (!n || Number.isNaN(n)) return '—';
    try {
        const fmt = new Intl.DateTimeFormat(undefined, {
            timeZone: timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone,
            hour: '2-digit', minute: '2-digit', hour12: false
        });
        return fmt.format(new Date(n));
    } catch { return new Date(n).toLocaleString(); }
};

export const fmtVolume = (v?: number | string | null) => {
    const n = v == null ? NaN : Number(v);
    if (Number.isNaN(n)) return '—';

    const abs = Math.abs(n);
    if (abs >= 1e12) return `${(n / 1e12).toFixed(1)}T`;
    if (abs >= 1e9) return `${(n / 1e9).toFixed(1)}B`;
    if (abs >= 1e6) return `${(n / 1e6).toFixed(1)}M`;
    if (abs >= 1e3) return `${(n / 1e3).toFixed(1)}K`;
    return n.toFixed(0);
};
