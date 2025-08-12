import { useMemo, useState } from 'react';
import { Box, Button, MenuItem, TextField, Alert } from '@mui/material';
import { useMutation } from '@tanstack/react-query';
import { postFeedback } from '@/api';
import type { FeedbackPayload } from '@/api/types';

export default function FeedbackPage() {
    const [type, setType] = useState<FeedbackPayload['type']>('bug');
    const [severity, setSeverity] = useState<FeedbackPayload['severity']>('normal');
    const [email, setEmail] = useState('');
    const [message, setMessage] = useState('');
    const [sent, setSent] = useState(false);

    const page = useMemo(() => window.location.pathname + window.location.search, []);

    const mutation = useMutation({
        mutationFn: (p: FeedbackPayload) => postFeedback(p),
        onSuccess: () => { setSent(true); setMessage(''); },
    });

    const canSend = message.trim().length >= 10 && !mutation.isPending;

    return (
        <Box sx={{ maxWidth: 720, mx: 'auto', display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
            <Box sx={{ fontSize: 24, fontWeight: 700 }}>Обратная связь</Box>

            {sent && <Alert severity="success">Спасибо! Мы получили ваше сообщение.</Alert>}
            {mutation.isError && <Alert severity="error">{(mutation.error as Error)?.message || 'Ошибка отправки'}</Alert>}

            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                <TextField
                    select label="Тип" value={type} onChange={e => setType(e.target.value as any)}
                    sx={{ minWidth: 220 }}
                >
                    <MenuItem value="bug">Баг</MenuItem>
                    <MenuItem value="idea">Идея/улучшение</MenuItem>
                    <MenuItem value="question">Вопрос</MenuItem>
                </TextField>

                <TextField
                    select label="Важность" value={severity} onChange={e => setSeverity(e.target.value as any)}
                    sx={{ minWidth: 220 }}
                >
                    <MenuItem value="low">Низкая</MenuItem>
                    <MenuItem value="normal">Средняя</MenuItem>
                    <MenuItem value="high">Высокая</MenuItem>
                    <MenuItem value="critical">Критичная</MenuItem>
                </TextField>

                <TextField
                    label="Email (необязательно)"
                    value={email} onChange={e => setEmail(e.target.value)}
                    type="email" sx={{ minWidth: 260, flex: 1 }}
                    placeholder="для обратной связи"
                />
            </Box>

            <TextField
                label="Сообщение"
                value={message}
                onChange={e => setMessage(e.target.value)}
                multiline minRows={6}
                placeholder="Опишите проблему/идею. Для бага добавьте шаги воспроизведения."
            />

            <Box sx={{ display: 'flex', gap: 2 }}>
                <Button
                    variant="contained"
                    disabled={!canSend}
                    onClick={() => mutation.mutate({
                        type, severity, message: message.trim(), email: email || undefined, page,
                        extra: { userAgent: navigator.userAgent, tz: Intl.DateTimeFormat().resolvedOptions().timeZone }
                    })}
                >
                    Отправить
                </Button>
                <Button variant="text" onClick={() => { setMessage(''); setSent(false); }}>Очистить</Button>
            </Box>
        </Box>
    );
}
