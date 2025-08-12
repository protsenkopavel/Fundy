import axios from 'axios';

export const api = axios.create({
    baseURL: '/api',
    timeout: 30000,
});

// перехватчик — удобные сообщения об ошибке
api.interceptors.response.use(
    r => r,
    (error) => {
        const msg = error?.response?.data?.message ?? error?.message ?? 'Request failed';
        return Promise.reject(new Error(msg));
    }
);