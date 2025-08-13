import {Autocomplete, Box, Button, CircularProgress, TextField} from '@mui/material';
import type {Exchange} from '@/api/types';

type Props = {
    exchanges: Exchange[];

    loading: boolean;
    onScan: () => void;
    onReset: () => void;
    onExportCsv?: () => void;

    selExchanges: Exchange[]; setSelExchanges: (v: Exchange[]) => void;

    // доп. фильтры (страницы сами решают передавать)
    minRate?: string; setMinRate?: (v: string) => void;
    minPriceSpread?: string; setMinPriceSpread?: (v: string) => void;

    // тайм-зоны
    timeZone: string;
    timeZones: string[];
    timeZoneValue: string; setTimeZoneValue: (v: string) => void;
};

export default function ScanToolbar(p: Props) {
    return (
        <Box sx={{display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', flexShrink: 0}}>
            <Autocomplete
                multiple disableCloseOnSelect
                options={p.exchanges}
                getOptionLabel={(o) => o.name}
                value={p.selExchanges}
                onChange={(_, v) => p.setSelExchanges(v)}
                renderInput={x => <TextField {...x} label="Биржи" size="small"/>}
                sx={{minWidth: 220}}
            />

            {p.setMinRate && (
                <TextField
                    label="Мин. фандинг (%)" placeholder="0.5"
                    value={p.minRate ?? ''} onChange={e => p.setMinRate!(e.target.value)}
                    type="number" inputProps={{step: 0.05, min: 0}} sx={{width: 160}} size="small"
                />
            )}

            {p.setMinPriceSpread && (
                <TextField
                    label="Мин. ценовой спред" placeholder="0.001"
                    value={p.minPriceSpread ?? ''} onChange={e => p.setMinPriceSpread!(e.target.value)}
                    type="number" inputProps={{step: 0.001, min: 0}} sx={{width: 180}} size="small"
                />
            )}

            <Autocomplete
                options={p.timeZones}
                value={p.timeZoneValue}
                onChange={(_, v) => p.setTimeZoneValue(v || p.timeZone)}
                isOptionEqualToValue={(o, v) => o === v}
                renderInput={(params) => <TextField {...params} label="Часовой пояс" size="small"/>}
                sx={{width: 280}}
            />

            <Button variant="contained" onClick={p.onScan} disabled={p.loading}>
                {p.loading ? <CircularProgress size={24}/> : 'Сканировать'}
            </Button>
            <Button variant="outlined" onClick={p.onReset} disabled={p.loading}>Сброс</Button>

            {p.onExportCsv && (
                <Button variant="outlined" onClick={p.onExportCsv} disabled={p.loading}>CSV</Button>
            )}
        </Box>
    );
}
