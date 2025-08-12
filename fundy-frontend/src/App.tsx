import { AppBar, Toolbar, Button, Container } from '@mui/material';
import { Routes, Route, Link } from 'react-router-dom';
import FundingPage from './pages/FundingPage';
import ArbitragePage from './pages/ArbitragePage';
import FeedbackPage from '@/pages/FeedbackPage';

export default function App() {
    return (
        <>
            <AppBar position="fixed">
                <Toolbar sx={{ gap: 1 }}>
                    <Button color="inherit" component={Link} to="/funding">Матрица фандингов</Button>
                    <Button color="inherit" component={Link} to="/arbitrage">Фьючерсный арбитраж</Button>
                    <Button color="inherit" component={Link} to="/feedback">Обратная связь</Button>
                </Toolbar>
            </AppBar>

            <Container sx={{ mt: '80px', mb: 2 }}>
                <Routes>
                    <Route path="/" element={<FundingPage />} />
                    <Route path="/funding" element={<FundingPage />} />
                    <Route path="/arbitrage" element={<ArbitragePage />} />
                    <Route path="/feedback" element={<FeedbackPage />} />
                </Routes>
            </Container>
        </>
    );
}
