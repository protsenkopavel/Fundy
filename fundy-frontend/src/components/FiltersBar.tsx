// src/components/FiltersBar.tsx
import { Box, Button, Autocomplete, TextField, Tooltip } from '@mui/material';
import { useMemo } from 'react';
import { useFilters } from '@/store/filters';
import { toCanonical } from '@/lib/symbols';

type Props = {
    exchangeOptions: string[]; // коды бирж: ["BYBIT", ...]
    tokenOptions?: string[];   // список доступных токенов (BASE/QUOTE)
};

export default function FiltersBar({ exchangeOptions, tokenOptions }: Props) {
    const { lists, setExWhitelist, setExBlacklist, setTokenWhitelist, setTokenBlacklist, reset } = useFilters();

    const exOpts = useMemo(() =>
        Array.from(new Set(exchangeOptions.map(s => String(s).toUpperCase()))), [exchangeOptions]);

    const tokenOpts = useMemo(() => {
        if (!tokenOptions?.length) return [];
        const set = new Set(tokenOptions.map(t => toCanonical(t)));
        return Array.from(set).sort();
    }, [tokenOptions]);

    return (
        <Box sx={{
            display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap',
            p: 1, border: '1px dashed rgba(255,255,255,0.12)', borderRadius: 1
        }}>
            <Tooltip title="Если пусто — разрешены все.">
                <Autocomplete
                    multiple
                    options={exOpts}
                    value={lists.exWhitelist ?? []}
                    onChange={(_, v) => setExWhitelist(v.length ? v : null)}
                    renderInput={(x) => <TextField {...x} size="small" label="Белый список бирж"/>}
                    sx={{minWidth: 260}}
                />
            </Tooltip>

            <Autocomplete
                multiple
                options={exOpts}
                value={lists.exBlacklist}
                onChange={(_, v) => setExBlacklist(v)}
                renderInput={(x) => <TextField {...x} size="small" label="Чёрный список бирж"/>}
                sx={{minWidth: 260}}
            />

            <Tooltip title="Если пусто — разрешены все. Значения в формате BASE/QUOTE, например BTC/USDT.">
                <Autocomplete
                    multiple
                    freeSolo
                    options={tokenOpts}
                    value={lists.tokenWhitelist ?? []}
                    onChange={(_, v) => setTokenWhitelist(v.length ? v : null)}
                    renderInput={(x) => <TextField {...x} size="small" label="Белый список токенов"/>}
                    sx={{minWidth: 320, flex: 1}}
                />
            </Tooltip>

            <Autocomplete
                multiple
                freeSolo
                options={tokenOpts}
                value={lists.tokenBlacklist}
                onChange={(_, v) => setTokenBlacklist(v)}
                renderInput={(x) => <TextField {...x} size="small" label="Чёрный список токенов"/>}
                sx={{minWidth: 320, flex: 1}}
            />

            <Button variant="text" onClick={reset}>Сбросить списки</Button>
        </Box>
    );
}
