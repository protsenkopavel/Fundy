import {useEffect, useMemo, useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Box, CircularProgress} from '@mui/material';
import {DataGrid, type GridColDef, GridToolbar} from '@mui/x-data-grid';
import {useSearchParams} from 'react-router-dom';

import {getExchanges, postSpotFuturesArbitrage} from '@/api';
import type {SpotFuturesArbitrageRequest, SpotFuturesArbitrageRow, Exchange} from '@/api/types';

import ScanToolbar from '@/components/ScanToolbar';
import {fmtPct, fmtPrice, fmtTs, pctColor, fmtVolume} from '@/lib/symbols';
import {BASE_TIMEZONES, EUROPE_TIMEZONES} from '@/lib/timezones';

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

export default function SpotFuturesArbitragePage() {
    const exchangesQuery = useQuery<Exchange[]>({queryKey: ['exchanges'], queryFn: getExchanges});

    const tzDefault = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    const tzOptions = useMemo(() => {
        const list = [tzDefault, 'UTC', ...EUROPE_TIMEZONES, ...BASE_TIMEZONES];
        const seen = new Set<string>();
        return list.filter(tz => !!tz && !seen.has(tz) && seen.add(tz));
    }, [tzDefault]);

    const [selExchanges, setSelExchanges] = useState<Exchange[]>([]);
    const [minPriceSpread, setMinPriceSpread] = useState<string>('');
    const [maxPriceSpread, setMaxPriceSpread] = useState<string>('');
    const [minVolume, setMinVolume] = useState<string>('');
    const [timeZone, setTimeZone] = useState<string>(tzDefault);

    const [searchParams, setSearchParams] = useSearchParams();
    useEffect(() => {
        if (!exchangesQuery.data) return;
        const exParam = searchParams.get('ex');
        const mpsParam = searchParams.get('mps');
        const maxMpsParam = searchParams.get('maxMps');
        const mvParam = searchParams.get('mv');
        const tzParam = searchParams.get('tz');

        if (mpsParam) setMinPriceSpread(mpsParam);
        if (maxMpsParam) setMaxPriceSpread(maxMpsParam);
        if (mvParam) setMinVolume(mvParam);
        if (tzParam) setTimeZone(tzParam);

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
            mv: minVolume || undefined,
            tz: timeZone || undefined,
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
    }, [selExchanges, minPriceSpread, maxPriceSpread, minVolume, timeZone]);

    const [rows, setRows] = useState<SpotFuturesArbitrageRow[]>([]);

    const lastReqRef = useRef<SpotFuturesArbitrageRequest | null>(null);

    const arbQuery = useQuery<SpotFuturesArbitrageRow[]>({
        queryKey: ['spot-futures-arbitrage'],
        enabled: false,
        queryFn: async () => {
            if (!lastReqRef.current) return [];
            return postSpotFuturesArbitrage(lastReqRef.current);
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
                headerName: 'Инструмент',
                width: 120,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => (
                    <Box sx={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        height: '100%',
                        fontFamily: '"Roboto Mono", ui-monospace, Menlo, Monaco, Consolas, "Liberation Mono", monospace',
                        fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.3,
                        textAlign: 'center'
                    }}>
                        {String(`${p.row?.instrument?.base ?? ''}/${p.row?.instrument?.quote ?? ''}`)}
                    </Box>
                )
            },
            {
                field: 'buyExchange',
                headerName: 'Спот покупка',
                width: 180,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const ex = String(p.value ?? '');
                    const link = p.row?.links?.[ex];
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
                            </Box>
                            <Box sx={{fontWeight: 700}}>{fmtPrice(price)}</Box>
                            <Box sx={{fontSize: 12, color: 'text.secondary'}}>
                                Объем 24ч: {fmtVolume(volume)} USDT
                            </Box>
                        </Box>
                    );

                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            width: '100%'
                        }}>
                            {link
                                ? <Box component="a" href={link} target="_blank" rel="noopener noreferrer"
                                        sx={{
                                            textDecoration: 'none',
                                            color: 'inherit',
                                            '&:hover': {textDecoration: 'underline'},
                                            display: 'flex',
                                            justifyContent: 'center',
                                            alignItems: 'center',
                                            height: '100%',
                                            width: '100%'
                                        }}
                                        title={`Открыть спот на ${ex}`}>{inner}</Box>
                                : inner}
                        </Box>
                    );
                }
            },
            {
                field: 'shortExchange',
                headerName: 'Фьючерс шорт',
                width: 180,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const ex = String(p.value ?? '');
                    const link = p.row?.links?.[ex];
                    const price = Number(p.row?.shortPrice);
                    const volume = Number(p.row?.shortVolume24h);
                    const fundingRate = Number(p.row?.fundingRate);
                    const nextFundingTs = p.row?.nextFundingTs;

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
                            </Box>
                            <Box sx={{fontWeight: 700}}>{fmtPrice(price)}</Box>
                            <Box sx={{fontSize: 12, color: pctColor(fundingRate), fontWeight: 600}}>
                                {fmtPct(fundingRate)}
                            </Box>
                            <Box sx={{fontSize: 11, color: 'text.secondary'}}>{fmtTs(nextFundingTs, timeZone)}</Box>
                            <Box sx={{fontSize: 12, color: 'text.secondary'}}>
                                Объем 24ч: {fmtVolume(volume)} USDT
                            </Box>
                        </Box>
                    );

                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            width: '100%'
                        }}>
                            {link
                                ? <Box component="a" href={link} target="_blank" rel="noopener noreferrer"
                                       sx={{
                                           textDecoration: 'none',
                                           color: 'inherit',
                                           '&:hover': {textDecoration: 'underline'},
                                           display: 'flex',
                                           justifyContent: 'center',
                                           alignItems: 'center',
                                           height: '100%',
                                           width: '100%'
                                       }}
                                       title={`Открыть фьючерс на ${ex}`}>{inner}</Box>
                                : inner}
                        </Box>
                    );
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
                    return (
                        <Box sx={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            fontWeight: 600,
                            textAlign: 'center'
                        }}>
                            {Number.isNaN(n) ? '—' : `${n.toFixed(2)}%`}
                        </Box>
                    );
                }
            }
        ];
    }, [timeZone]);

    const handleScan = () => {
        const minPriceSpreadPct = minPriceSpread ? Number(minPriceSpread) / 100 : undefined;
        const maxPriceSpreadPct = maxPriceSpread ? Number(maxPriceSpread) / 100 : undefined;
        const minVolumeValue = minVolume ? Number(minVolume) : undefined;

        lastReqRef.current = {
            exchanges: selExchanges.length ? selExchanges.map(e => e.code ?? e.name) : undefined,
            minSpread: Number.isFinite(minPriceSpreadPct as number) ? minPriceSpreadPct : undefined,
            maxSpread: Number.isFinite(maxPriceSpreadPct as number) ? maxPriceSpreadPct : undefined,
            minVolume: Number.isFinite(minVolumeValue as number) ? minVolumeValue : undefined
        };
        arbQuery.refetch();
    };

    const handleReset = () => {
        setSelExchanges([]);
        setMinPriceSpread('');
        setMaxPriceSpread('');
        setMinVolume('');
        setTimeZone(tzDefault);
    };

    if (exchangesQuery.isLoading) return <Box sx={{p: 3}}>Загрузка…</Box>;

    return (
        <Box sx={{display: 'flex', flexDirection: 'column', gap: 2, height: 'calc(100dvh - 120px)'}}>
            <ScanToolbar
                exchanges={exchangesQuery.data ?? []}
                timeZone={tzDefault}
                timeZones={tzOptions}
                loading={arbQuery.isFetching}
                onScan={handleScan}
                onReset={handleReset}
                selExchanges={selExchanges} setSelExchanges={setSelExchanges}
                minPriceSpread={minPriceSpread} setMinPriceSpread={setMinPriceSpread}
                maxPriceSpread={maxPriceSpread} setMaxPriceSpread={setMaxPriceSpread}
                minVolume={minVolume} setMinVolume={setMinVolume}
                timeZoneValue={timeZone} setTimeZoneValue={setTimeZone}
            />

            <Box sx={{flex: 1, minHeight: 0}}>
                <DataGrid
                    rows={rows}
                    columns={columns.length ? columns : [{field: 'coin', headerName: 'Монета', width: 140, align: 'center', headerAlign: 'center'}]}
                    getRowId={(r) => `${r.instrument?.base ?? ''}-${r.buyExchange}-${r.shortExchange}`}
                    loading={arbQuery.isFetching}
                    slots={{toolbar: GridToolbar, loadingOverlay: CenterOverlay}}
                    slotProps={{toolbar: {showQuickFilter: true, quickFilterProps: {debounceMs: 300}}}}
                    getRowHeight={() => 80}
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