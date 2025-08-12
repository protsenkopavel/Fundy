import {useEffect, useMemo, useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Box} from '@mui/material';
import {DataGrid, type GridColDef, GridToolbar} from '@mui/x-data-grid';

import {getExchanges, getTokens, postFunding} from '@/api';
import type {Exchange, FundingRow} from '@/api/types';
import ScanToolbar from '@/components/ScanToolbar';
import {fmtPct, fmtTs, labelFromCanonical, pctColor, toCanonical} from '@/lib/symbols';
import {BASE_TIMEZONES, EUROPE_TIMEZONES} from '@/lib/timezones';

function CenterOverlay() {
    return (
        <Box sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'background.paper',
            opacity: 0.7
        }}>
            Загрузка…
        </Box>
    );
}

export default function FundingPage() {
    const exchangesQuery = useQuery<Exchange[]>({queryKey: ['exchanges'], queryFn: getExchanges});
    useQuery({queryKey: ['tokens'], queryFn: getTokens, staleTime: 5 * 60_000}); // прогрев

    const tzDefault = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    const tzOptions = useMemo(() => {
        const list = [tzDefault, 'UTC', ...EUROPE_TIMEZONES, ...BASE_TIMEZONES];
        const seen = new Set<string>();
        return list.filter(tz => !!tz && !seen.has(tz) && seen.add(tz));
    }, [tzDefault]);

    const [selEx, setSelEx] = useState<Exchange[]>([]);
    const [minRate, setMinRate] = useState('');
    const [tz, setTz] = useState(tzDefault);

    const [rows, setRows] = useState<any[]>([]);
    const [cols, setCols] = useState<GridColDef[]>([]);

    const lastReqRef = useRef<{ exchanges?: string[]; minFundingRate?: string; timeZone?: string } | null>(null);

    const funding = useQuery<FundingRow[]>({
        queryKey: ['funding'],
        enabled: false,
        queryFn: async () => {
            if (!lastReqRef.current) return [];
            return postFunding(lastReqRef.current);
        },
        refetchOnMount: false,
        staleTime: 5 * 60_000,
    });

    useEffect(() => {
        const data = (funding.data ?? []) as any[];
        const byCanon: Record<string, any> = {};
        const exSet = new Set<string>();

        for (const r of data) {
            const ex = String(r.exchange || r.ex || r.exchangeType || '');
            if (!ex) continue;
            exSet.add(ex);
            const raw = r.symbol ?? r.nativeSymbol ?? '';
            const canon = toCanonical(raw);
            if (!byCanon[canon]) byCanon[canon] = {id: canon, instrument: labelFromCanonical(canon)};
            byCanon[canon][ex] = {rate: r.fundingRate, ts: r.nextFundingTs, link: r.link}; // ← добавили ссылку
        }

        const exList = Array.from(exSet);

        const base: GridColDef[] = [{
            field: 'instrument',
            headerName: 'Инструмент',
            width: 160,
            sortable: true,
            renderCell: (p) => {
                // Ссылка тикера = первая доступная ссылка по приоритету бирж или из exList
                const prefer = ['BYBIT', 'OKX', 'BITGET', 'MEXC', 'GATEIO', 'KUCOIN', 'COINEX', 'BINGX', 'HTX', ...exList];
                const href = prefer
                    .map(ex => p.row?.[ex]?.link)
                    .find(Boolean);

                const label = (
                    <Box sx={{
                        fontFamily: '"Roboto Mono", ui-monospace',
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        letterSpacing: 0.3
                    }}>
                        {p.value}
                    </Box>
                );

                return href
                    ? (
                        <Box
                            component="a"
                            href={href}
                            target="_blank"
                            rel="noopener noreferrer"
                            sx={{textDecoration: 'none', color: 'inherit', '&:hover': {textDecoration: 'underline'}}}
                            title="Открыть инструмент"
                        >
                            {label}
                        </Box>
                    )
                    : label;
            }
        }];

        const exCols: GridColDef[] = exList.map((ex): GridColDef => ({
            field: ex,
            headerName: ex,
            flex: 1,
            minWidth: 180,
            align: 'center',
            headerAlign: 'center',
            sortable: false,
            renderCell: (params) => {
                const cell = params.row?.[ex] as { rate?: number; ts?: number; link?: string } | undefined;
                if (!cell) {
                    return (
                        <Box sx={{
                            width: '100%', height: '100%',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            color: '#98A2B3'
                        }}>—</Box>
                    );
                }
                const color = pctColor(cell.rate);
                const inner = (
                    <Box sx={{
                        display: 'flex', flexDirection: 'column',
                        alignItems: 'center', justifyContent: 'center',
                        gap: 0.25, lineHeight: 1.15, textAlign: 'center'
                    }}>
                        <Box sx={{fontWeight: 700, color}}>{fmtPct(cell.rate)}</Box>
                        <Box sx={{fontSize: 12, color: '#667085'}}>{fmtTs(cell.ts, tz)}</Box>
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
                            ? (
                                <Box
                                    component="a"
                                    href={cell.link}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    sx={{
                                        textDecoration: 'none',
                                        color: 'inherit',
                                        '&:hover': {textDecoration: 'underline'}
                                    }}
                                    title={`Открыть на ${ex}`}
                                >
                                    {inner}
                                </Box>
                            )
                            : inner}
                    </Box>
                );
            }
        }));

        setCols([...base, ...exCols]);
        setRows(Object.values(byCanon));
    }, [funding.data, tz]);

    const handleScan = () => {
        const parsed = minRate ? Number(minRate) : NaN;
        const minFundingRate = Number.isNaN(parsed) ? undefined : String(parsed / 100);

        lastReqRef.current = {
            exchanges: selEx.length ? selEx.map(e => e.code ?? e.name) : undefined,
            minFundingRate,
            timeZone: undefined
        };
        funding.refetch();
    };

    const handleReset = () => {
        setSelEx([]);
        setMinRate('');
        setTz(tzDefault);
    };

    if (exchangesQuery.isLoading) return <Box sx={{p: 3}}>Загрузка…</Box>;

    return (
        <Box sx={{display: 'flex', flexDirection: 'column', gap: 2, height: 'calc(100dvh - 120px)'}}>
            <ScanToolbar
                exchanges={exchangesQuery.data ?? []}
                timeZone={tzDefault}
                timeZones={tzOptions}
                loading={funding.isFetching}
                onScan={handleScan}
                onReset={handleReset}
                selExchanges={selEx} setSelExchanges={setSelEx}
                minRate={minRate} setMinRate={setMinRate}
                timeZoneValue={tz} setTimeZoneValue={setTz}
            />

            <Box sx={{flex: 1, minHeight: 0}}>
                <DataGrid
                    rows={rows}
                    columns={cols.length ? cols : [{field: 'instrument', headerName: 'Инструмент', width: 160}]}
                    getRowId={(r) => r.id}
                    loading={funding.isFetching}
                    slots={{toolbar: GridToolbar, loadingOverlay: CenterOverlay}}
                    slotProps={{toolbar: {showQuickFilter: true, quickFilterProps: {debounceMs: 300}}}}
                    getRowHeight={() => 60}
                    disableRowSelectionOnClick
                    density="compact"
                    rowBufferPx={300}
                    sx={{
                        height: '100%',
                        width: '100%',
                        '& .MuiDataGrid-columnHeaders': {
                            textTransform: 'uppercase', letterSpacing: 0.6, fontWeight: 700, fontSize: 12.5,
                            backgroundColor: '#F8FAFC', borderBottom: '1px solid #EEF2F6'
                        },
                        '& .MuiDataGrid-row:nth-of-type(even)': {backgroundColor: '#FCFCFD'},
                        '& .MuiDataGrid-cell:focus, & .MuiDataGrid-columnHeader:focus': {outline: 'none'}
                    }}
                />
            </Box>
        </Box>
    );
}
