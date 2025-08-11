import { AppBar, Toolbar, Button, Container } from '@mui/material';
import { Routes, Route, Link } from 'react-router-dom';
import FundingPage from './pages/FundingPage';
import ArbitragePage from './pages/ArbitragePage';

export default function App() {
    return (
        <>
            <AppBar position="static">
                <Toolbar>
                    <Button color="inherit" component={Link} to="/funding">Фандинги</Button>
                    <Button color="inherit" component={Link} to="/arbitrage">Арбитраж</Button>
                </Toolbar>
            </AppBar>

            <Container sx={{ mt: 2 }}>
                <Routes>
                    <Route path="/" element={<FundingPage />} />
                    <Route path="/funding" element={<FundingPage />} />
                    <Route path="/arbitrage" element={<ArbitragePage />} />
                </Routes>
            </Container>
        </>
    );
}
