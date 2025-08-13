// src/lib/filters.ts
import { toCanonical } from './symbols';

export type Lists = {
    exWhitelist: string[] | null;     // null или [] => все разрешены
    exBlacklist: string[];
    tokenWhitelist: string[] | null;  // null или [] => все разрешены
    tokenBlacklist: string[];
};

export const DEFAULT_LISTS: Lists = {
    exWhitelist: null,
    exBlacklist: [],
    tokenWhitelist: null,
    tokenBlacklist: [],
};

export function isExchangeAllowed(code?: string, lists?: Partial<Lists>) {
    const c = String(code ?? '').toUpperCase();
    const wl = lists?.exWhitelist ?? null;
    const bl = lists?.exBlacklist ?? [];
    const inWhite = !wl || wl.length === 0 || wl.includes(c);
    const inBlack = bl.includes(c);
    return inWhite && !inBlack;
}

export function isTokenAllowed(token?: string, lists?: Partial<Lists>) {
    const canon = toCanonical(String(token ?? ''));
    const wl = lists?.tokenWhitelist ?? null;
    const bl = lists?.tokenBlacklist ?? [];
    const inWhite = !wl || wl.length === 0 || wl.includes(canon);
    const inBlack = bl.includes(canon);
    return inWhite && !inBlack;
}

// аккуратные сеттеры с нормализацией
export function normExList(arr: string[] | null | undefined) {
    if (!arr || arr.length === 0) return null;
    const set = new Set(arr.map(s => String(s).trim().toUpperCase()).filter(Boolean));
    return Array.from(set);
}
export function normTokenList(arr: string[] | null | undefined) {
    if (!arr || arr.length === 0) return null;
    const set = new Set(arr.map(s => toCanonical(String(s).trim())).filter(Boolean));
    return Array.from(set);
}
