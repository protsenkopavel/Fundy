import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './styles.css';

const qc = new QueryClient({
    defaultOptions: {
        queries: {
            // при возврате на страницу не перезапрашиваем мгновенно
            staleTime: 5 * 60_000,   // 5 минут
            gcTime: 15 * 60_000,     // держим кэш 15 минут
            refetchOnMount: false,
            refetchOnWindowFocus: false,
            retry: 1,
        },
        mutations: { retry: 0 },
    },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <QueryClientProvider client={qc}>
            <BrowserRouter>
                <App />
            </BrowserRouter>
        </QueryClientProvider>
    </React.StrictMode>
);
