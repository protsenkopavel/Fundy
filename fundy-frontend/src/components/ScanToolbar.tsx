// src/components/ScanToolbar.tsx
import { useEffect, useState } from 'react';
import { Autocomplete, Box, Button, Checkbox, CircularProgress, FormControlLabel, MenuItem, TextField } from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import type { Exchange, Token } from '@/api/types';

type Props = {
    exchanges: Exchange[];
    tokens?: Token[];
    timeZone: string;
    timeZones: string[];
    loading: boolean;
    onScan: () => void;
    onReset: () => void;
    onExportCsv?: () => void;

    selExchanges: Exchange[]; setSelExchanges: (v: Exchange[]) => void;
    minRate?: string; setMinRate?: (v: string) => void;
    minPriceSpread?: string; setMinPriceSpread?: (v: string) => void;
    timeZoneValue: string; setTimeZoneValue: (v: string) => void;
};

export default function ScanToolbar(p: Props) {
    const [auto, setAuto] = useState(false);
    const [period, setPeriod] = useState(15);

    useEffect(() => {
        if (!auto) return;
        p.onScan();
        const id = setInterval(p.onScan, Math.max(5, period) * 1000);
        return () => clearInterval(id);
    }, [auto, period]); // eslint-disable-line

    return (
        <Box sx={{display:'flex',gap:2,alignItems:'center',flexWrap:'wrap',flexShrink:0}}>
            <Autocomplete
                multiple disableCloseOnSelect
                options={p.exchanges}
                getOptionLabel={(o) => o.name}
                value={p.selExchanges}
                onChange={(_, v) => p.setSelExchanges(v)}
                renderInput={x => <TextField {...x} label="Биржи" size="small" />}
                sx={{minWidth:220}}
            />

            {p.setMinRate && (
                <TextField
                    label="Мин. фандинг (%)" placeholder="0.5"
                    value={p.minRate ?? ''} onChange={e => p.setMinRate!(e.target.value)}
                    type="number" inputProps={{step:0.05, min:0}} sx={{width:160}} size="small"
                />
            )}
            {p.setMinPriceSpread && (
                <TextField
                    label="Мин. ценовой спред" placeholder="0.001"
                    value={p.minPriceSpread ?? ''} onChange={e => p.setMinPriceSpread!(e.target.value)}
                    type="number" inputProps={{step:0.001, min:0}} sx={{width:180}} size="small"
                />
            )}

            <TextField select label="Часовой пояс" value={p.timeZoneValue} onChange={e => p.setTimeZoneValue(e.target.value)}
                       sx={{width:240}} size="small">
                {p.timeZones.map(tz => <MenuItem key={tz} value={tz}>{tz}</MenuItem>)}
            </TextField>

            <Button variant="contained" onClick={p.onScan} disabled={p.loading}>
                {p.loading ? <CircularProgress size={24}/> : 'Сканировать'}
            </Button>
            <Button variant="outlined" onClick={p.onReset} startIcon={<RestartAltIcon/>} disabled={p.loading}>Сброс</Button>
            {p.onExportCsv && (
                <Button variant="outlined" onClick={p.onExportCsv} startIcon={<DownloadIcon/>} disabled={p.loading}>CSV</Button>
            )}

            <FormControlLabel
                control={<Checkbox checked={auto} onChange={(_,v)=>setAuto(v)} />}
                label="Автообновление"
            />
            {auto && (
                <TextField
                    label="Период, сек" size="small" type="number" sx={{width:120}}
                    value={period} onChange={e=>setPeriod(Math.max(5, Number(e.target.value)||15))}
                />
            )}
        </Box>
    );
}
