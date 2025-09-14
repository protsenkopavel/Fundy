import {useEffect, useMemo, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Box, CircularProgress, Chip} from '@mui/material';
import {DataGrid, type GridColDef, GridToolbar} from '@mui/x-data-grid';
import {useSearchParams} from 'react-router-dom';

import {getSpotTickers, getExchanges} from '@/api';
import type {TickerData, Exchange} from '@/api/types';

import {fmtPct, fmtPrice, fmtVolume} from '@/lib/symbols';

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

export default function SpotCoinsPage() {
    const exchangesQuery = useQuery<Exchange[]>({queryKey: ['exchanges'], queryFn: getExchanges});

    const [selExchanges, setSelExchanges] = useState<Exchange[]>([]);
    const [searchParams, setSearchParams] = useSearchParams();

    useEffect(() => {
        if (!exchangesQuery.data) return;
        const exParam = searchParams.get('ex');

        if (exParam) {
            const codes = exParam.split(',').map(s => s.trim()).filter(Boolean);
            const byCode = new Map(exchangesQuery.data.map(e => [String(e.code ?? e.name), e] as const));
            setSelExchanges(codes.map(c => byCode.get(c)).filter(Boolean) as Exchange[]);
        }
    }, [!!exchangesQuery.data]);

    useEffect(() => {
        const next = new URLSearchParams(searchParams.toString());
        const exCodes = selExchanges.map(e => e.code ?? e.name).join(',');
        const entries: Record<string, string | undefined> = {
            ex: exCodes || undefined,
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
    }, [selExchanges]);

    const [rows, setRows] = useState<TickerData[]>([]);

    const tickersQuery = useQuery<TickerData[]>({
        queryKey: ['spot-tickers', selExchanges.map(e => e.code ?? e.name).join(',')],
        queryFn: async () => {
            const exchanges = selExchanges.length ? selExchanges.map(e => e.code ?? e.name) : undefined;
            return getSpotTickers({exchanges});
        },
        enabled: selExchanges.length > 0,
        refetchOnMount: false,
        staleTime: 30 * 1000, // 30 seconds
    });

    useEffect(() => {
        const data = tickersQuery.data ?? [];
        // Sort by price change percentage (descending - highest changes first)
        const sorted = [...data].sort((a, b) => {
            const aChange = a.priceChangePercent24h ?? 0;
            const bChange = b.priceChangePercent24h ?? 0;
            return bChange - aChange;
        });
        setRows(sorted);
    }, [tickersQuery.data]);

    const columns: GridColDef[] = useMemo(() => {
        return [
            {
                field: 'symbol',
                headerName: 'Монета',
                width: 140,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const data = p.row as TickerData;
                    const coinText = `${data.instrument.baseAsset}/${data.instrument.quoteAsset}`;

                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%'
                        }}>
                            {data.tradingLink ? (
                                <Box
                                    component="a"
                                    href={data.tradingLink}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    sx={{
                                        fontFamily: '"Roboto Mono", ui-monospace, Menlo, Monaco, Consolas, "Liberation Mono", monospace',
                                        fontWeight: 700,
                                        textTransform: 'uppercase',
                                        letterSpacing: 0.3,
                                        textAlign: 'center',
                                        color: 'primary.main',
                                        textDecoration: 'none',
                                        '&:hover': {
                                            textDecoration: 'underline',
                                            color: 'primary.dark'
                                        },
                                        cursor: 'pointer'
                                    }}
                                    title={`Открыть ${coinText} на ${data.instrument.exchangeType}`}
                                >
                                    {coinText}
                                </Box>
                            ) : (
                                <Box sx={{
                                    fontFamily: '"Roboto Mono", ui-monospace, Menlo, Monaco, Consolas, "Liberation Mono", monospace',
                                    fontWeight: 700,
                                    textTransform: 'uppercase',
                                    letterSpacing: 0.3,
                                    textAlign: 'center',
                                    color: 'text.primary'
                                }}>
                                    {coinText}
                                </Box>
                            )}
                        </Box>
                    );
                }
            },
            {
                field: 'exchange',
                headerName: 'Биржа',
                width: 120,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const data = p.row as TickerData;
                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            fontWeight: 600,
                            textTransform: 'uppercase'
                        }}>
                            {data.instrument.exchangeType}
                        </Box>
                    );
                }
            },
            {
                field: 'lastPrice',
                headerName: 'Цена',
                width: 120,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const price = Number(p.value);
                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            fontWeight: 600
                        }}>
                            {fmtPrice(price)}
                        </Box>
                    );
                }
            },
            {
                field: 'priceChangePercent24h',
                headerName: 'Изменение 24ч',
                width: 140,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const change = Number(p.value);
                    const color = change > 0 ? '#22c55e' : change < 0 ? '#ef4444' : '#6b7280';
                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            fontWeight: 600,
                            color
                        }}>
                            {Number.isNaN(change) ? '—' : `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`}
                        </Box>
                    );
                }
            },
            {
                field: 'volume24h',
                headerName: 'Объем 24ч',
                width: 140,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const volume = Number(p.value);
                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            fontWeight: 500,
                            color: 'text.secondary'
                        }}>
                            {fmtVolume(volume)}
                        </Box>
                    );
                }
            },
            {
                field: 'high24h',
                headerName: 'Макс 24ч',
                width: 120,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const high = Number(p.value);
                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            fontWeight: 500,
                            color: '#22c55e'
                        }}>
                            {high === 0 ? '—' : fmtPrice(high)}
                        </Box>
                    );
                }
            },
            {
                field: 'low24h',
                headerName: 'Мин 24ч',
                width: 120,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const low = Number(p.value);
                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            fontWeight: 500,
                            color: '#ef4444'
                        }}>
                            {low === 0 ? '—' : fmtPrice(low)}
                        </Box>
                    );
                }
            }
        ];
    }, []);

    if (exchangesQuery.isLoading) return <Box sx={{p: 3}}>Загрузка…</Box>;

    return (
        <Box sx={{display: 'flex', flexDirection: 'column', gap: 2, height: 'calc(100dvh - 120px)'}}>
            <Box sx={{display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center'}}>
                <Box sx={{fontWeight: 600, mr: 1}}>Биржи:</Box>
                {exchangesQuery.data?.map(exchange => (
                    <Chip
                        key={exchange.code ?? exchange.name}
                        label={exchange.name}
                        onClick={() => {
                            const exchangeKey = exchange.code ?? exchange.name;
                            const isSelected = selExchanges.some(e => (e.code ?? e.name) === exchangeKey);
                            if (isSelected) {
                                setSelExchanges(selExchanges.filter(e => (e.code ?? e.name) !== exchangeKey));
                            } else {
                                setSelExchanges([...selExchanges, exchange]);
                            }
                        }}
                        variant={selExchanges.some(e => (e.code ?? e.name) === (exchange.code ?? exchange.name)) ? 'filled' : 'outlined'}
                        size="small"
                    />
                ))}
            </Box>

            <Box sx={{flex: 1, minHeight: 0}}>
                <DataGrid
                    rows={rows}
                    columns={columns.length ? columns : [{field: 'symbol', headerName: 'Монета', width: 140, align: 'center', headerAlign: 'center'}]}
                    getRowId={(r) => `${r.instrument.baseAsset}-${r.instrument.quoteAsset}-${r.instrument.exchangeType}`}
                    loading={tickersQuery.isFetching}
                    slots={{toolbar: GridToolbar, loadingOverlay: CenterOverlay}}
                    slotProps={{toolbar: {showQuickFilter: true, quickFilterProps: {debounceMs: 300}}}}
                    getRowHeight={() => 60}
                    disableRowSelectionOnClick
                    density="compact"
                    rowBufferPx={300}
                    sx={{
                        height: '100%', width: '100%',
                        '& .MuiDataGrid-cell': {
                            fontSize: 13,
                            py: 0.8,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        },
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