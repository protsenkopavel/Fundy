import { AppBar, Toolbar, Button, Container, CssBaseline, ThemeProvider } from '@mui/material';
import { Routes, Route, Link } from 'react-router-dom';
import FundingPage from '@/pages/FundingPage';
import ArbitragePage from '@/pages/ArbitragePage';
import FeedbackPage from '@/pages/FeedbackPage';
import AboutPage from '@/pages/AboutPage';          // ← new
import theme from '@/theme';

export default function App() {
    return (
        <ThemeProvider theme={theme}>
            <CssBaseline />

            <AppBar position="fixed" elevation={0}>
                <Toolbar sx={{gap: 1}}>
                    <Button color="inherit" component={Link} to="/funding">| Матрица фандингов |</Button>
                    <Button color="inherit" component={Link} to="/arbitrage">| Фьючерсный арбитраж |</Button>
                    <Button color="inherit" component={Link} to="/feedback">| Обратная связь |</Button>
                    <Button color="inherit" component={Link} to="/about">| О сервисе |</Button> {/* ← new */}
                </Toolbar>
            </AppBar>

            <Container sx={{ mt: 10, mb: 2 }}>
                <Routes>
                    <Route path="/" element={<FundingPage />} />
                    <Route path="/funding" element={<FundingPage />} />
                    <Route path="/arbitrage" element={<ArbitragePage />} />
                    <Route path="/feedback" element={<FeedbackPage />} />
                    <Route path="/about" element={<AboutPage />} />         {/* ← new */}
                </Routes>
            </Container>
        </ThemeProvider>
    );
}
