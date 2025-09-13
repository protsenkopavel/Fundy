import {useEffect, useMemo, useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Box, CircularProgress} from '@mui/material';
import {DataGrid, type GridColDef, GridToolbar} from '@mui/x-data-grid';
import {useSearchParams} from 'react-router-dom';

import {getExchanges, postSpotArbitrage} from '@/api';
import type {SpotArbitrageRequest, SpotArbitrageRow, Exchange} from '@/api/types';

import ScanToolbar from '@/components/ScanToolbar';
import {fmtPct, fmtPrice, fmtVolume} from '@/lib/symbols';

const getStatusColor = (status: string) => {
    switch (status) {
        case 'ENABLED': return '#22c55e';
        case 'DISABLED': return '#ef4444';
        case 'UNKNOWN': return '#f59e0b';
        default: return '#6b7280';
    }
};

const getStatusLabel = (status: string) => {
    switch (status) {
        case 'ENABLED': return { text: '✓', color: '#22c55e' };
        case 'DISABLED': return { text: '✗', color: '#ef4444' };
        case 'UNKNOWN': return { text: '?', color: '#f59e0b' };
        default: return { text: '?', color: '#6b7280' };
    }
};

const formatVolumeInUSDT = (volume: number, price: number) => {
    return fmtPrice(volume * price);
};

function CenterOverlay() {
    return (
        <Box sx={{
            position: 'absolute', inset: 0, display: 'flex',
            alignItems: 'center', justifyContent: 'center',
            bgcolor: 'background.paper', opacity: 0.7
        }}>
            <CircularProgress/>
        </Box>
    );
}

export default function SpotArbitragePage() {
    const exchangesQuery = useQuery<Exchange[]>({queryKey: ['exchanges'], queryFn: getExchanges});

    const [selExchanges, setSelExchanges] = useState<Exchange[]>([]);
    const [minPriceSpread, setMinPriceSpread] = useState<string>('');
    const [maxPriceSpread, setMaxPriceSpread] = useState<string>('');

    const [searchParams, setSearchParams] = useSearchParams();
    useEffect(() => {
        if (!exchangesQuery.data) return;
        const exParam = searchParams.get('ex');
        const mpsParam = searchParams.get('mps');
        const maxMpsParam = searchParams.get('maxMps');

        if (mpsParam) setMinPriceSpread(mpsParam);
        if (maxMpsParam) setMaxPriceSpread(maxMpsParam);

        if (exParam) {
            const codes = exParam.split(',').map(s => s.trim()).filter(Boolean);
            const byCode = new Map(exchangesQuery.data.map(e => [String(e.code ?? e.name), e] as const));
            setSelExchanges(codes.map(c => byCode.get(c)).filter(Boolean) as Exchange[]);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [!!exchangesQuery.data]);

    useEffect(() => {
        const next = new URLSearchParams(searchParams.toString());
        const exCodes = selExchanges.map(e => e.code ?? e.name).join(',');
        const entries: Record<string, string | undefined> = {
            ex: exCodes || undefined,
            mps: minPriceSpread || undefined,
            maxMps: maxPriceSpread || undefined,
        };
        let changed = false;
        for (const [k, v] of Object.entries(entries)) {
            const cur = searchParams.get(k) ?? undefined;
            if (cur !== v) {
                changed = true;
                if (v == null) next.delete(k); else next.set(k, v);
            }
        }
        if (changed) setSearchParams(next, {replace: true});
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selExchanges, minPriceSpread, maxPriceSpread]);

    const [rows, setRows] = useState<SpotArbitrageRow[]>([]);

    const lastReqRef = useRef<SpotArbitrageRequest | null>(null);

    const arbQuery = useQuery<SpotArbitrageRow[]>({
        queryKey: ['spot-arbitrage'],
        enabled: false,
        queryFn: async () => {
            if (!lastReqRef.current) return [];
            return postSpotArbitrage(lastReqRef.current);
        },
        refetchOnMount: false,
        staleTime: 5 * 60_000,
    });

    useEffect(() => {
        const data = arbQuery.data ?? [];
        setRows(data);
    }, [arbQuery.data]);

    const columns: GridColDef[] = useMemo(() => {
        return [
            {
                field: 'coin',
                headerName: 'Монета',
                width: 120,
                align: 'left',
                headerAlign: 'left',
                renderCell: (p) => (
                    <Box sx={{
                        fontFamily: '"Roboto Mono", ui-monospace, Menlo, Monaco, Consolas, "Liberation Mono", monospace',
                        fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.3
                    }}>
                        {String(p.row?.pair ?? '')}
                    </Box>
                )
            },
            {
                field: 'buyExchange',
                headerName: 'Биржа покупки',
                width: 200,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const ex = String(p.value ?? '');
                    const link = p.row?.links?.[ex];
                    const status = p.row?.withdrawalStatus;
                    const price = Number(p.row?.buyPrice);
                    const volume = Number(p.row?.buyVolume24h);

                    const inner = (
                        <Box sx={{
                            display: 'flex', flexDirection: 'column',
                            alignItems: 'center', gap: 0.25, lineHeight: 1.15, whiteSpace: 'nowrap'
                        }}>
                            <Box sx={{
                                px: 0.5, fontSize: 12, fontWeight: 900,
                                color: '#22c55e',
                                textTransform: 'uppercase',
                                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.2
                            }}>
                                {ex}
                                <Box sx={{
                                    fontSize: 10,
                                    color: getStatusLabel(status).color,
                                    fontWeight: 'bold'
                                }}>
                                    {getStatusLabel(status).text} вывод
                                </Box>
                            </Box>
                            <Box sx={{fontWeight: 700}}>{fmtPrice(price)}</Box>
                            <Box sx={{fontSize: 12, color: 'text.secondary'}}>
                                Объем 24ч: {fmtVolume(volume)} USDT
                            </Box>
                        </Box>
                    );

                    return link
                        ? <Box component="a" href={link} target="_blank" rel="noopener noreferrer"
                               sx={{
                                   textDecoration: 'none',
                                   color: 'inherit',
                                   '&:hover': {textDecoration: 'underline'}
                               }}
                               title={`Открыть на ${ex} (${status?.toLowerCase()} вывод)`}>{inner}</Box>
                        : inner;
                }
            },
            {
                field: 'sellExchange',
                headerName: 'Биржа продажи',
                width: 200,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const ex = String(p.value ?? '');
                    const link = p.row?.links?.[ex];
                    const status = p.row?.depositStatus;
                    const price = Number(p.row?.sellPrice);
                    const volume = Number(p.row?.sellVolume24h);

                    const inner = (
                        <Box sx={{
                            display: 'flex', flexDirection: 'column',
                            alignItems: 'center', gap: 0.25, lineHeight: 1.15, whiteSpace: 'nowrap'
                        }}>
                            <Box sx={{
                                px: 0.5, fontSize: 12, fontWeight: 900,
                                color: '#ef4444',
                                textTransform: 'uppercase',
                                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.2
                            }}>
                                {ex}
                                <Box sx={{
                                    fontSize: 10,
                                    color: getStatusLabel(status).color,
                                    fontWeight: 'bold'
                                }}>
                                    {getStatusLabel(status).text} ввод
                                </Box>
                            </Box>
                            <Box sx={{fontWeight: 700}}>{fmtPrice(price)}</Box>
                            <Box sx={{fontSize: 12, color: 'text.secondary'}}>
                                Объем 24ч: {fmtVolume(volume)} USDT
                            </Box>
                        </Box>
                    );

                    return link
                        ? <Box component="a" href={link} target="_blank" rel="noopener noreferrer"
                               sx={{
                                   textDecoration: 'none',
                                   color: 'inherit',
                                   '&:hover': {textDecoration: 'underline'}
                               }}
                               title={`Открыть на ${ex} (${status?.toLowerCase()} ввод)`}>{inner}</Box>
                        : inner;
                }
            },
            {
                field: 'priceSpread',
                headerName: 'Спред цены',
                width: 140,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const n = Number(p.row?.priceSpread);
                    return <Box sx={{fontWeight: 600}}>{Number.isNaN(n) ? '—' : `${n.toFixed(2)}%`}</Box>;
                }
            }
        ];
    }, []);

    const handleScan = () => {
        const minPriceSpreadPct = minPriceSpread ? Number(minPriceSpread) / 100 : undefined;
        const maxPriceSpreadPct = maxPriceSpread ? Number(maxPriceSpread) / 100 : undefined;

        lastReqRef.current = {
            exchanges: selExchanges.length ? selExchanges.map(e => e.code ?? e.name) : undefined,
            minSpread: Number.isFinite(minPriceSpreadPct as number) ? minPriceSpreadPct : undefined,
            maxSpread: Number.isFinite(maxPriceSpreadPct as number) ? maxPriceSpreadPct : undefined
        };
        arbQuery.refetch();
    };

    const handleReset = () => {
        setSelExchanges([]);
        setMinPriceSpread('');
        setMaxPriceSpread('');
    };

    if (exchangesQuery.isLoading) return <Box sx={{p: 3}}>Загрузка…</Box>;

    return (
        <Box sx={{display: 'flex', flexDirection: 'column', gap: 2, height: 'calc(100dvh - 120px)'}}>
            <ScanToolbar
                exchanges={exchangesQuery.data ?? []}
                timeZone="UTC"
                timeZones={["UTC"]}
                timeZoneValue="UTC" setTimeZoneValue={() => {}}
                loading={arbQuery.isFetching}
                onScan={handleScan}
                onReset={handleReset}
                selExchanges={selExchanges} setSelExchanges={setSelExchanges}
                minPriceSpread={minPriceSpread} setMinPriceSpread={setMinPriceSpread}
                maxPriceSpread={maxPriceSpread} setMaxPriceSpread={setMaxPriceSpread}
            />

            <Box sx={{flex: 1, minHeight: 0}}>
                <DataGrid
                    rows={rows}
                    columns={columns.length ? columns : [{field: 'coin', headerName: 'Монета', width: 140}]}
                    getRowId={(r) => `${r.coin}-${r.buyExchange}-${r.sellExchange}`}
                    loading={arbQuery.isFetching}
                    slots={{toolbar: GridToolbar, loadingOverlay: CenterOverlay}}
                    slotProps={{toolbar: {showQuickFilter: true, quickFilterProps: {debounceMs: 300}}}}
                    getRowHeight={() => 80}
                    disableRowSelectionOnClick
                    density="compact"
                    rowBufferPx={300}
                    sx={{
                        height: '100%', width: '100%',
                        '& .MuiDataGrid-cell': {fontSize: 13, py: 0.8},
                        '& .MuiDataGrid-columnHeaders': {
                            textTransform: 'uppercase', letterSpacing: 0.6, fontWeight: 700, fontSize: 12.5,
                            backgroundColor: 'rgba(255,255,255,0.04)', borderBottom: '1px solid rgba(255,255,255,0.08)',
                        },
                        '& .MuiDataGrid-row:nth-of-type(even)': {backgroundColor: 'rgba(255,255,255,0.02)'},
                        '& .MuiDataGrid-cell:focus, & .MuiDataGrid-columnHeader:focus': {outline: 'none'}
                    }}
                />
            </Box>
        </Box>
    );
}