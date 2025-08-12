// src/pages/FeedbackPage.tsx
import {useMemo, useRef, useState} from 'react';
import {Alert, Box, Button, LinearProgress, MenuItem, TextField} from '@mui/material';
import {useMutation} from '@tanstack/react-query';
import {postFeedback} from '@/api';
import type {FeedbackPayload} from '@/api/types';

export default function FeedbackPage() {
    const [type, setType] = useState<FeedbackPayload['type']>('bug');
    const [severity, setSeverity] = useState<FeedbackPayload['severity']>('normal');
    const [email, setEmail] = useState('');
    const [message, setMessage] = useState('');
    const [sent, setSent] = useState(false);
    const [serverError, setServerError] = useState<string | null>(null);

    const page = useMemo(() => window.location.pathname + window.location.search, []);
    const messageRef = useRef<HTMLInputElement | HTMLTextAreaElement | null>(null);

    const mutation = useMutation({
        mutationFn: (p: FeedbackPayload) => postFeedback(p),
        onSuccess: () => {
            setSent(true);
            setMessage('');
            setServerError(null);
        },
        onError: (e: any) => setServerError(e?.message || 'Ошибка отправки'),
    });

    const MIN_LEN = 10;
    const msgTooShort = message.trim().length < MIN_LEN;

    const handleSend = () => {
        if (msgTooShort) {
            messageRef.current?.focus();
            return;
        }
        setServerError(null);
        mutation.mutate({
            type,
            severity,
            message: message.trim(),
            email: email || undefined,
            page,
            extra: {userAgent: navigator.userAgent, tz: Intl.DateTimeFormat().resolvedOptions().timeZone},
        });
    };

    return (
        <Box sx={{maxWidth: 720, mx: 'auto', display: 'flex', flexDirection: 'column', gap: 2, mt: 2}}>
            <Box sx={{fontSize: 24, fontWeight: 700}}>Обратная связь</Box>

            {mutation.isPending && <LinearProgress/>}

            {sent && (
                <Alert severity="success" onClose={() => setSent(false)}>
                    Спасибо! Мы получили ваше сообщение.
                </Alert>
            )}
            {serverError && (
                <Alert severity="error" onClose={() => setServerError(null)}>
                    {serverError}
                </Alert>
            )}

            <Box sx={{display: 'flex', gap: 2, flexWrap: 'wrap'}}>
                <TextField
                    select
                    label="Тип"
                    value={type}
                    onChange={(e) => setType(e.target.value as any)}
                    sx={{minWidth: 220}}
                    size="small"
                >
                    <MenuItem value="bug">Баг</MenuItem>
                    <MenuItem value="idea">Идея/улучшение</MenuItem>
                    <MenuItem value="question">Вопрос</MenuItem>
                </TextField>

                <TextField
                    select
                    label="Важность"
                    value={severity}
                    onChange={(e) => setSeverity(e.target.value as any)}
                    sx={{minWidth: 220}}
                    size="small"
                >
                    <MenuItem value="low">Низкая</MenuItem>
                    <MenuItem value="normal">Средняя</MenuItem>
                    <MenuItem value="high">Высокая</MenuItem>
                    <MenuItem value="critical">Критичная</MenuItem>
                </TextField>

                <TextField
                    label="Email (необязательно)"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    type="email"
                    sx={{minWidth: 260, flex: 1}}
                    size="small"
                    placeholder="для обратной связи"
                    autoComplete="email"
                />
            </Box>

            <TextField
                label="Сообщение"
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                multiline
                minRows={6}
                inputRef={messageRef}
                placeholder="Опишите проблему/идею. Для бага — шаги воспроизведения."
                error={!!message && msgTooShort}
                helperText={!!message && msgTooShort ? `Минимум ${MIN_LEN} символов` : ' '}
            />

            <Box sx={{display: 'flex', gap: 2}}>
                <Button variant="contained" onClick={handleSend} disabled={mutation.isPending}>
                    Отправить
                </Button>
                <Button
                    variant="text"
                    onClick={() => {
                        setMessage('');
                        setSent(false);
                        setServerError(null);
                    }}
                    disabled={mutation.isPending}
                >
                    Очистить
                </Button>
            </Box>
        </Box>
    );
}
