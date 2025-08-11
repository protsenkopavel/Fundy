import { useQuery, useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { getExchanges, getTokens, postArbitrage } from '../api';
import {
    Box, Autocomplete, TextField, Button, CircularProgress, MenuItem
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';

type Exchange = { name: string; code?: string };
type Token = { symbol: string; baseAsset: string; quoteAsset: string };

type ArbitrageRequest = {
  exchanges?: string[];
  minFundingRate?: number;
  minPerpetualPrice?: number;
  timeZone?: string;
};

export default function ArbitragePage() {
  const exchangesQuery = useQuery<Exchange[]>({ queryKey: ['exchanges'], queryFn: getExchanges });
  const tokensQuery = useQuery<Token[]>({ queryKey: ['tokens'], queryFn: getTokens });

  const exchanges = exchangesQuery.data ?? [];
  const tokens = tokensQuery.data ?? [];

  const fallbackExchanges: Exchange[] = [{ name: 'DemoExchange', code: 'demo' }];
  const fallbackTokens: Token[] = [{ symbol: 'BTCUSDT', baseAsset: 'BTC', quoteAsset: 'USDT' }];

  const displayExchanges = exchanges.length ? exchanges : fallbackExchanges;
  const displayTokens = tokens.length ? tokens : fallbackTokens;

  const [selExchanges, setSelExchanges] = useState<Exchange[]>([]);
  const [minRate, setMinRate] = useState<string>('');
  const [minPriceSpread, setMinPriceSpread] = useState<string>('');
  const [loadingArb, setLoadingArb] = useState(false);

  const tzDefault = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  const tzOptions = [
    tzDefault,
    'UTC',
    'Europe/Moscow',
    'Europe/London',
    'America/New_York',
    'Asia/Shanghai',
    'Asia/Tokyo'
  ].filter((v, i, a) => a.indexOf(v) === i);
  const [timeZone, setTimeZone] = useState<string>(tzDefault);

  const [arbitrageRows, setArbitrageRows] = useState<any[]>([]);
  const [arbColumns, setArbColumns] = useState<any[]>([]);

  const arbitrage = useMutation<ArbitrageRequest[], Error, ArbitrageRequest, unknown>({
    mutationFn: (req: ArbitrageRequest) => postArbitrage(req)
  });

  const handleScan = () => {
    setLoadingArb(true);
    const parsed = minRate ? Number(minRate) : NaN;
    const minFunding = Number.isNaN(parsed) ? undefined : (parsed / 100);
    const minPerp = minPriceSpread ? Number(minPriceSpread) : undefined;
    const req = {
      exchanges: selExchanges.length ? selExchanges.map(e => e.code ?? e.name) : undefined,
      minFundingRate: minFunding,
      minPerpetualPrice: minPerp,
      timeZone: timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone
    };
    arbitrage.mutate(req, {
      onSuccess: (data: any[]) => {
        const exSet = new Set<string>();
        (data ?? []).forEach((it: any) => {
          Object.keys(it.prices ?? {}).forEach((ex: string) => exSet.add(ex));
          Object.keys(it.fundingRates ?? {}).forEach((ex: string) => exSet.add(ex));
        });
        const exchangesList = Array.from(exSet);

        const cols: any[] = [
          { field: 'token', headerName: 'Токен', width: 120 }
        ];

        exchangesList.forEach(ex => {
          cols.push({
            field: `${ex}_price`,
            headerName: `${ex} (price)`,
            width: 110,
            renderCell: (params: any) => {
              const rawDirect = params.row?.[`${ex}_price`];
              const rowCell = params.row?.[ex];
              const priceVal = rawDirect ?? rowCell?.price;
              if (priceVal == null) return <div>—</div>;
              const formatted = Number(priceVal);
              return <div style={{ fontWeight: 600 }}>{Number.isNaN(formatted) ? String(priceVal) : formatted.toFixed(3)}</div>;
            }
          });
        });

        exchangesList.forEach(ex => {
          cols.push({
            field: `${ex}_funding`,
            headerName: `${ex} (funding)`,
            width: 140,
            renderCell: (params: any) => {
              const direct = params.row?.[`${ex}_funding`];
              const rowCell = params.row?.[ex];
              const frVal = direct?.fundingRate ?? rowCell?.fundingRate;
              const tsVal = direct?.nextFundingTs ?? rowCell?.nextFundingTs;
              if (frVal == null && tsVal == null) return <div>—</div>;
              const fr = frVal == null ? null : Number(frVal);
              const ts = tsVal == null ? null : Number(tsVal);
              const frText = fr == null || Number.isNaN(fr) ? '—' : (fr * 100).toFixed(4) + '%';
              const tsText = ts ? (isNaN(new Date(ts).getTime()) ? '—' : new Date(ts).toLocaleString()) : '—';
              return (
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <div style={{ fontWeight: 600 }}>{frText}</div>
                  <div style={{ fontSize: 12, color: '#666' }}>{tsText}</div>
                </div>
              );
            }
          });
        });

        cols.push(
          {
            field: 'priceSpread',
            headerName: 'Лучший спред (price)',
            width: 140,
            renderCell: (params: any) => {
              const v = params.row?.priceSpread;
              if (v == null || Number.isNaN(Number(v))) return <div>—</div>;
              const n = Number(v);
              return <div>{Number.isNaN(n) ? String(v) : n.toFixed(6)}</div>;
            }
          },
          {
            field: 'fundingSpread',
            headerName: 'Лучший спред (funding)',
            width: 160,
            renderCell: (params: any) => {
              const v = params.row?.fundingSpread;
              if (v == null || Number.isNaN(Number(v))) return <div>—</div>;
              return <div>{(Number(v) * 100).toFixed(4)}%</div>;
            }
          },
          {
            field: 'decision',
            headerName: 'Решение',
            width: 220,
            renderCell: (params: any) => {
              const d = params.row?.decision;
              return <div>{d ? `long: ${d.longEx ?? '—'}, short: ${d.shortEx ?? '—'}` : '—'}</div>;
            }
          }
        );

        const rows = (data ?? []).map((it: any, idx: number) => {
          const row: any = {
            id: `arb_${idx}`,
            token: it.token ?? `token_${idx}`,
            priceSpread: it.priceSpread,
            fundingSpread: it.fundingSpread,
            decision: it.decision
          };
          exchangesList.forEach(ex => {
            const price = it.prices?.[ex];
            const fr = it.fundingRates?.[ex];
            const nft = it.nextFundingTs?.[ex];
            row[ex] = {
              price,
              fundingRate: fr,
              nextFundingTs: nft
            };
            row[`${ex}_price`] = price;
            row[`${ex}_funding`] = {
              fundingRate: fr,
              nextFundingTs: nft
            };
          });
          return row;
        });

        setArbColumns(cols);
        setArbitrageRows(rows);
      },
      onSettled: () => setLoadingArb(false)
    });
  };

  if (exchangesQuery.isLoading || tokensQuery.isLoading) {
    return <div>Загрузка...</div>;
  }

  return (
    <>
      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <Autocomplete
          multiple
          disableCloseOnSelect
          options={displayExchanges}
          getOptionLabel={o => (o as Exchange).name}
          onChange={(_, v) => setSelExchanges(v as Exchange[])}
          renderInput={p => <TextField {...p} label="Биржи" />}
          sx={{ minWidth: 200 }}
        />

        <Autocomplete
          multiple
          options={displayTokens}
          getOptionLabel={t => (t as Token).symbol}
          renderInput={p => <TextField {...p} label="Токены" />}
          sx={{ minWidth: 250 }}
        />

        <TextField
          label="Мин. фандинг (%)"
          placeholder="Напр. 0.5"
          value={minRate}
          onChange={e => setMinRate(e.target.value)}
          type="number"
          inputProps={{ step: 0.05, min: 0 }}
          sx={{ width: 140 }}
        />

        <TextField
          label="Мин. ценовой спред"
          placeholder="Напр. 0.001"
          value={minPriceSpread}
          onChange={e => setMinPriceSpread(e.target.value)}
          type="number"
          inputProps={{ step: 0.001, min: 0 }}
          sx={{ width: 160 }}
        />

        <TextField
          select
          label="Часовой пояс"
          value={timeZone}
          onChange={e => setTimeZone(e.target.value)}
          sx={{ width: 220 }}
          size="small"
        >
          {tzOptions.map(tz => <MenuItem key={tz} value={tz}>{tz}</MenuItem>)}
        </TextField>

        <Button variant="contained" onClick={handleScan} disabled={loadingArb}>
          {loadingArb ? <CircularProgress size={24}/> : 'Сканировать'}
        </Button>
      </Box>

      <div style={{ height: 640, width: '100%' }}>
        <DataGrid
          rows={arbitrageRows}
          columns={arbColumns && arbColumns.length ? arbColumns : [
            { field: 'token', headerName: 'Токен', minWidth: 120, flex: 0.8 },
            { field: 'priceSpread', headerName: 'Spread (price)', minWidth: 120, flex: 0.6,
              renderCell: (params: any) => {
                const v = params.row?.priceSpread;
                if (v == null || Number.isNaN(Number(v))) return <div>—</div>;
                return <div>{Number(v).toFixed(6)}</div>;
              }
            },
            { field: 'fundingSpread', headerName: 'Spread (funding)', minWidth: 140, flex: 0.7,
              renderCell: (params: any) => {
                const v = params.row?.fundingSpread;
                if (v == null || Number.isNaN(Number(v))) return <div>—</div>;
                return <div>{(Number(v) * 100).toFixed(4)}%</div>;
              }
            },
            { field: 'decision', headerName: 'Решение', minWidth: 220, flex: 1,
              renderCell: (params: any) => {
                const d = params.row?.decision;
                return <div>{d ? `long: ${d.longEx ?? '—'}, short: ${d.shortEx ?? '—'}` : '—'}</div>;
              }
            }
          ]}
          pageSizeOptions={[25,50,100]}
          initialState={{ pagination: { paginationModel: { pageSize: 100 } } }}
          pagination
          density="comfortable"
          disableRowSelectionOnClick
          sx={{
            width: '100%',
            '& .MuiDataGrid-cell': { py: 0.8 },
            '& .MuiDataGrid-columnHeaders': { background: '#fafafa' }
          }}
        />
      </div>
    </>
  );
}
