import { createTheme } from '@mui/material/styles';

const theme = createTheme({
    palette: {
        mode: 'dark',
        background: {
            default: '#0B0F19', // тёмный фон страницы
            paper:   '#111827', // карточки / панели
        },
        primary:   { main: '#7C3AED' }, // фиолетовый акцент
        secondary: { main: '#06B6D4' },
        success:   { main: '#22c55e' }, // мягкий зелёный
        error:     { main: '#ef4444' }, // мягкий красный
        warning:   { main: '#f59e0b' },
        divider: 'rgba(255,255,255,0.08)',
        text: {
            primary:   '#E5E7EB',
            secondary: '#9CA3AF',
        },
    },
    shape: { borderRadius: 12 },
    typography: {
        fontFamily: [
            'Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Ubuntu',
            'Cantarell', 'Noto Sans', 'Helvetica Neue', 'Arial', 'sans-serif'
        ].join(','),
        button: { textTransform: 'none', fontWeight: 600 },
    },
    components: {
        MuiAppBar: {
            styleOverrides: {
                root: {
                    background: 'rgba(17,24,39,0.8)',
                    backdropFilter: 'blur(8px)',
                    borderBottom: '1px solid rgba(255,255,255,0.08)',
                },
            },
        },
        MuiPaper: {
            styleOverrides: {
                root: { backgroundImage: 'none' },
            },
        },
    },
});

export default theme;
