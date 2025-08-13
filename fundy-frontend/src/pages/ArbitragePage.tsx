import {useEffect, useMemo, useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Box, CircularProgress} from '@mui/material';
import {DataGrid, type GridColDef, GridToolbar} from '@mui/x-data-grid';
import {useSearchParams} from 'react-router-dom';

import {getExchanges, postArbitrage} from '@/api';
import type {ArbitrageRequest, ArbitrageRow, Exchange} from '@/api/types';

import ScanToolbar from '@/components/ScanToolbar';
import {fmtPct, fmtPrice, fmtTs, labelFromCanonical, pctColor, toCanonical} from '@/lib/symbols';
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

export default function ArbitragePage() {
    const exchangesQuery = useQuery<Exchange[]>({queryKey: ['exchanges'], queryFn: getExchanges});

    const tzDefault = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    const tzOptions = useMemo(() => {
        const list = [tzDefault, 'UTC', ...EUROPE_TIMEZONES, ...BASE_TIMEZONES];
        const seen = new Set<string>();
        return list.filter(tz => !!tz && !seen.has(tz) && seen.add(tz));
    }, [tzDefault]);

    const [selExchanges, setSelExchanges] = useState<Exchange[]>([]);
    const [minRate, setMinRate] = useState<string>('');
    const [minPriceSpread, setMinPriceSpread] = useState<string>('');
    const [timeZone, setTimeZone] = useState<string>(tzDefault);

    const [searchParams, setSearchParams] = useSearchParams();
    useEffect(() => {
        if (!exchangesQuery.data) return;
        const exParam = searchParams.get('ex');
        const tzParam = searchParams.get('tz');
        const mrParam = searchParams.get('min');
        const mpsParam = searchParams.get('mps');

        if (tzParam) setTimeZone(tzParam);
        if (mrParam) setMinRate(mrParam);
        if (mpsParam) setMinPriceSpread(mpsParam);

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
            tz: timeZone || undefined,
            min: minRate || undefined,
            mps: minPriceSpread || undefined,
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
    }, [selExchanges, timeZone, minRate, minPriceSpread]);

    const [rows, setRows] = useState<any[]>([]);
    const [exchangeList, setExchangeList] = useState<string[]>([]);

    const lastReqRef = useRef<ArbitrageRequest | null>(null);

    const arbQuery = useQuery<ArbitrageRow[]>({
        queryKey: ['arbitrage'],
        enabled: false,
        queryFn: async () => {
            if (!lastReqRef.current) return [];
            return postArbitrage(lastReqRef.current);
        },
        refetchOnMount: false,
        staleTime: 5 * 60_000,
    });

    useEffect(() => {
        const data = arbQuery.data ?? [];
        const exSet = new Set<string>();
        for (const it of data as any[]) {
            Object.keys(it?.prices ?? {}).forEach(ex => exSet.add(ex));
            Object.keys(it?.fundingRates ?? {}).forEach(ex => exSet.add(ex));
            Object.keys(it?.nextFundingTs ?? {}).forEach(ex => exSet.add(ex));
        }
        const exList = Array.from(exSet);
        setExchangeList(exList);

        const mapped = (data as any[]).map((it, i) => {
            const row: any = {
                id: it?.token ?? `arb_${i}`,
                token: it?.token ?? `token_${i}`,
                priceSpread: it?.priceSpread,
                fundingSpread: it?.fundingSpread,
                decision: it?.decision,
                __links: it?.links || {},
            };
            exList.forEach(ex => {
                row[ex] = {
                    price: it?.prices?.[ex],
                    fundingRate: it?.fundingRates?.[ex],
                    nextFundingTs: it?.nextFundingTs?.[ex],
                    link: it?.links?.[ex],
                };
            });
            return row;
        });

        setRows(mapped);
    }, [arbQuery.data]);

    const columns: GridColDef[] = useMemo(() => {
        const baseCols: GridColDef[] = [
            {
                field: 'token',
                headerName: 'Инструмент',
                width: 160,
                align: 'left',
                headerAlign: 'left',
                renderCell: (p) => {
                    const canon = toCanonical(String(p.value ?? ''));
                    return (
                        <Box sx={{
                            fontFamily: '"Roboto Mono", ui-monospace, Menlo, Monaco, Consolas, "Liberation Mono", monospace',
                            fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.3
                        }}>
                            {labelFromCanonical(canon)}
                        </Box>
                    );
                }
            },
            {
                field: 'priceSpread',
                headerName: 'Спред цены',
                width: 120,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const n = Number(p.row?.priceSpread);
                    return <Box sx={{fontWeight: 600}}>{Number.isNaN(n) ? '—' : n.toFixed(6)}</Box>;
                }
            },
            {
                field: 'fundingSpread',
                headerName: 'Спред фандинга',
                width: 140,
                align: 'center',
                headerAlign: 'center',
                renderCell: (p) => {
                    const n = Number(p.row?.fundingSpread);
                    return (
                        <Box sx={{fontWeight: 700, color: pctColor(n)}}>
                            {Number.isNaN(n) ? '—' : fmtPct(n)}
                        </Box>
                    );
                }
            },
            {
                field: 'decision',
                headerName: 'Комбинация',
                minWidth: 220,
                flex: 0.9,
                align: 'center',
                headerAlign: 'center',
                sortable: false,
                renderCell: (p) => {
                    const d = p.row?.decision as { longEx?: string; shortEx?: string } | undefined;
                    if (!d || (!d.longEx && !d.shortEx)) return <Box sx={{color: 'text.secondary'}}>—</Box>;

                    const tag = (text: string, color: string) => (
                        <Box sx={{
                            px: 0.5, fontSize: 12, fontWeight: 900,
                            color, border: 'none !important', background: 'transparent',
                            textTransform: 'uppercase', whiteSpace: 'nowrap',
                            width: 120, display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                            {text}
                        </Box>
                    );

                    return (
                        <Box sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                            {d.longEx && tag(`LONG ${d.longEx}`, '#22c55e')}
                            {d.shortEx && tag(`SHORT ${d.shortEx}`, '#ef4444')}
                        </Box>
                    );
                }
            }
        ];

        const exCols: GridColDef[] = exchangeList.map((ex): GridColDef => ({
            field: ex,
            headerName: ex,
            flex: 1,
            minWidth: 190,
            align: 'center',
            headerAlign: 'center',
            sortable: false,
            renderCell: (params) => {
                const cell = params.row?.[ex] as {
                    price?: number;
                    fundingRate?: number;
                    nextFundingTs?: number;
                    link?: string
                } | undefined;
                if (!cell) return <Box sx={{color: 'text.secondary'}}>—</Box>;

                const inner = (
                    <Box sx={{
                        display: 'flex', flexDirection: 'column',
                        alignItems: 'center', gap: 0.25, lineHeight: 1.15, whiteSpace: 'nowrap'
                    }}>
                        <Box sx={{fontWeight: 700}}>{fmtPrice(cell.price)}</Box>
                        <Box sx={{fontSize: 12, color: pctColor(cell.fundingRate), fontWeight: 600}}>
                            {fmtPct(cell.fundingRate)}
                        </Box>
                        <Box sx={{fontSize: 11, color: 'text.secondary'}}>{fmtTs(cell.nextFundingTs, timeZone)}</Box>
                    </Box>
                );

                return (
                    <Box sx={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center'
                    }}>
                        {cell.link
                            ? <Box component="a" href={cell.link} target="_blank" rel="noopener noreferrer"
                                   sx={{
                                       textDecoration: 'none',
                                       color: 'inherit',
                                       '&:hover': {textDecoration: 'underline'}
                                   }}
                                   title={`Открыть на ${ex}`}>{inner}</Box>
                            : inner}
                    </Box>
                );
            }
        }));

        return [...baseCols, ...exCols];
    }, [exchangeList, timeZone]);

    const handleScan = () => {
        const minFunding = minRate ? Number(minRate) / 100 : undefined;
        const minPerp = minPriceSpread ? Number(minPriceSpread) : undefined;

        lastReqRef.current = {
            exchanges: selExchanges.length ? selExchanges.map(e => e.code ?? e.name) : undefined,
            minFundingRate: Number.isFinite(minFunding as number) ? minFunding : undefined,
            minPerpetualPrice: Number.isFinite(minPerp as number) ? minPerp : undefined,
            timeZone
        };
        arbQuery.refetch();
    };

    const handleReset = () => {
        setSelExchanges([]);
        setMinRate('');
        setMinPriceSpread('');
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
                minRate={minRate} setMinRate={setMinRate}
                minPriceSpread={minPriceSpread} setMinPriceSpread={setMinPriceSpread}
                timeZoneValue={timeZone} setTimeZoneValue={setTimeZone}
            />

            <Box sx={{flex: 1, minHeight: 0}}>
                <DataGrid
                    rows={rows}
                    columns={columns.length ? columns : [{field: 'token', headerName: 'Инструмент', width: 140}]}
                    getRowId={(r) => r.id}
                    loading={arbQuery.isFetching}
                    slots={{toolbar: GridToolbar, loadingOverlay: CenterOverlay}}
                    slotProps={{toolbar: {showQuickFilter: true, quickFilterProps: {debounceMs: 300}}}}
                    getRowHeight={() => 64}
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
