// src/main.tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {BrowserRouter} from 'react-router-dom';
import App from './App';
import './styles.css';
import ErrorBoundary from '@/components/ErrorBoundary';

import {FiltersProvider} from '@/store/filters';


const qc = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 5 * 60_000,
            gcTime: 15 * 60_000,
            refetchOnMount: false,
            refetchOnWindowFocus: false,
            retry: 1,
        },
        mutations: {retry: 0},
    },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <QueryClientProvider client={qc}>
            <BrowserRouter>
                <ErrorBoundary>
                    <App />
                </ErrorBoundary>
            </BrowserRouter>
        </QueryClientProvider>
    </React.StrictMode>
);
