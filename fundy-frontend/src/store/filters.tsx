// src/store/filters.tsx
import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { DEFAULT_LISTS, Lists, normExList, normTokenList } from '@/lib/filters';

const LS_KEY = 'fundy.filters.v1';

type Ctx = {
    lists: Lists;
    setExWhitelist: (v: string[] | null) => void;
    setExBlacklist: (v: string[]) => void;
    setTokenWhitelist: (v: string[] | null) => void;
    setTokenBlacklist: (v: string[]) => void;
    reset: () => void;
};

const FiltersContext = createContext<Ctx | null>(null);

export function FiltersProvider({ children }: { children: React.ReactNode }) {
    const [lists, setLists] = useState<Lists>(() => {
        try {
            const raw = localStorage.getItem(LS_KEY);
            if (!raw) return DEFAULT_LISTS;
            const parsed = JSON.parse(raw);
            return {
                exWhitelist: normExList(parsed.exWhitelist ?? null),
                exBlacklist: normExList(parsed.exBlacklist ?? []) ?? [],
                tokenWhitelist: normTokenList(parsed.tokenWhitelist ?? null),
                tokenBlacklist: normTokenList(parsed.tokenBlacklist ?? []) ?? [],
            };
        } catch {
            return DEFAULT_LISTS;
        }
    });

    useEffect(() => {
        localStorage.setItem(LS_KEY, JSON.stringify(lists));
    }, [lists]);

    const api = useMemo<Ctx>(() => ({
        lists,
        setExWhitelist: (v) => setLists(prev => ({ ...prev, exWhitelist: normExList(v) })),
        setExBlacklist: (v) => setLists(prev => ({ ...prev, exBlacklist: normExList(v) ?? [] })),
        setTokenWhitelist: (v) => setLists(prev => ({ ...prev, tokenWhitelist: normTokenList(v) })),
        setTokenBlacklist: (v) => setLists(prev => ({ ...prev, tokenBlacklist: normTokenList(v) ?? [] })),
        reset: () => setLists(DEFAULT_LISTS),
    }), [lists]);

    return <FiltersContext.Provider value={api}>{children}</FiltersContext.Provider>;
}

export function useFilters() {
    const ctx = useContext(FiltersContext);
    if (!ctx) throw new Error('useFilters must be used within FiltersProvider');
    return ctx;
}
