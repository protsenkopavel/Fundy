import { useQuery, useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { getExchanges, getTokens, postFunding } from '../api';
import { Box, Autocomplete, TextField, Button, CircularProgress, MenuItem } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';

export default function FundingPage() {
  const exchangesQuery = useQuery<any[]>({ queryKey: ['exchanges'], queryFn: getExchanges });
  const tokensQuery = useQuery<any[]>({ queryKey: ['tokens'], queryFn: getTokens });

  const exchanges = exchangesQuery.data ?? [];
  const tokens = tokensQuery.data ?? [];

  const [selExchanges, setSelExchanges] = useState<any[]>([]);
  const [minRate, setMinRate] = useState('');
  const [loadingFunding, setLoadingFunding] = useState(false);
  const [fundingRows, setFundingRows] = useState<any[]>([]);

  const tzDefault = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  const tzOptions = [tzDefault, 'UTC', 'Europe/Moscow', 'Europe/London', 'America/New_York', 'Asia/Shanghai', 'Asia/Tokyo'].filter((v, i, a) => a.indexOf(v) === i);
  const [timeZone, setTimeZone] = useState<string>(tzDefault);

  const funding = useMutation({ mutationFn: (req: any) => postFunding(req) });

  const handleScan = () => {
    setLoadingFunding(true);
    const parsed = minRate ? Number(minRate) : NaN;
    const minFundingRateStr = Number.isNaN(parsed) ? undefined : String(parsed / 100);

    funding.mutate({
      exchanges: selExchanges.length ? selExchanges.map((e: any) => (e.code ?? e.name ?? e.type)) : undefined,
      minFundingRate: minFundingRateStr,
      timeZone: timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone,
    }, {
      onSuccess: (data: any[]) => {
        const rows = (data ?? []).map((r: any, i: number) => {
          const fr = r?.fundingRate;
          const ts = r?.nextFundingTs;
          return {
            id: `${r.exchange ?? 'ex'}_${r.symbol ?? i}_${i}`,
            exchange: r.exchange,
            symbol: r.symbol,
            fundingRate: fr == null ? undefined : (Number(fr)),
            nextFundingTs: ts == null ? undefined : Number(ts),
            __raw: r
          };
        });
        setFundingRows(rows);
      },
      onSettled: () => setLoadingFunding(false),
    });
  };

  if (exchangesQuery.isLoading || tokensQuery.isLoading) {
    return <div>Загрузка...</div>;
  }


  return (
    <>
      <Box sx={{ display: 'flex', gap: 2, mb: 2, alignItems: 'center' }}>
        <Autocomplete
          multiple
          disableCloseOnSelect
          options={exchanges}
          getOptionLabel={(o: any) => o.name}
          onChange={(_, v) => setSelExchanges(v)}
          renderInput={p => <TextField {...p} label="Биржи" />}
          sx={{ minWidth: 200 }}
        />

        <Autocomplete
          multiple
          options={tokens}
          getOptionLabel={(o: any) => o?.symbol ?? o?.nativeSymbol ?? ((o?.baseAsset ?? '') + (o?.quoteAsset ?? ''))}
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
          select
          label="Часовой пояс"
          value={timeZone}
          onChange={e => setTimeZone(e.target.value)}
          sx={{ width: 220 }}
          size="small"
        >
          {tzOptions.map(tz => <MenuItem key={tz} value={tz}>{tz}</MenuItem>)}
        </TextField>

        <Button variant="contained" onClick={handleScan} disabled={loadingFunding}>
          {loadingFunding ? <CircularProgress size={24}/> : 'Сканировать'}
        </Button>
      </Box>

      <DataGrid
        rows={fundingRows}
        columns={[
          { field: 'exchange', headerName: 'Биржа', width: 120 },
          { field: 'symbol', headerName: 'Тикер', width: 160 },
          {
            field: 'fundingRate',
            headerName: 'Фандинг %',
            width: 140,
            renderCell: (params: any) => {
              const row = params.row || {};
              const raw = row.fundingRate ?? row.__raw?.fundingRate;
              const num = raw == null ? NaN : Number(raw);
              if (Number.isNaN(num)) return <div>—</div>;
              return <div>{(num * 100).toFixed(4)}%</div>;
            }
          },
          {
            field: 'nextFundingTs',
            headerName: 'След. время',
            flex: 1,
            renderCell: (params: any) => {
              const row = params.row || {};
              const rawTs = row.nextFundingTs ?? row.__raw?.nextFundingTs;
              const ts = rawTs == null ? NaN : Number(rawTs);
              if (Number.isNaN(ts) || !ts) return <div>—</div>;
              const d = new Date(ts);
              if (isNaN(d.getTime())) return <div>—</div>;
              return <div>{d.toLocaleString()}</div>;
            }
          }
        ]}
        autoHeight={false}
        density="comfortable"
        disableRowSelectionOnClick
        sx={{ height: fundingRows && fundingRows.length ? Math.min(Math.max(fundingRows.length * 52 + 120, 320), 1200) : 320 }}
      />
    </>
  );
}
