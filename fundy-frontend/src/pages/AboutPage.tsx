import {Box, Link, Paper, Stack, Typography} from '@mui/material';

export default function AboutPage() {
    return (
        <Stack spacing={2}>
            <Typography variant="h5" fontWeight={800}>О сервисе</Typography>

            <Paper sx={{p: 2}}>
                <Typography variant="subtitle1" fontWeight={700}>Что это?</Typography>
                <Typography variant="body1" sx={{mt: 1}}>
                    Инструмент для мониторинга фандингов и поиска ценовых дисбалансов между биржами по бессрочным фьючерсам.
                    Сервис не хранит средств пользователей и не выполняет сделки.
                </Typography>
            </Paper>

            <Paper sx={{p: 2}}>
                <Typography variant="subtitle1" fontWeight={700}>Дисклеймер</Typography>
                <Typography variant="body1" sx={{mt: 1}}>
                    Информация носит исключительно исследовательский характер и <b>не является инвестиционной рекомендацией</b>.
                    Рынки волатильны, используйте собственное усмотрение и управление рисками.
                </Typography>
            </Paper>

            <Paper sx={{p: 2}}>
                <Typography variant="subtitle1" fontWeight={700}>Поддержать проект</Typography>
                <Box sx={{mt: 1, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace'}}>
                    <Box sx={{mb: 1}}><b>USDT (TRC20):</b> <code>TPxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx</code></Box>
                    <Box sx={{mb: 1}}><b>USDT (ERC20):</b> <code>0xYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY</code></Box>
                    <Box sx={{mb: 1}}><b>BTC (SegWit):</b> <code>bc1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq</code></Box>
                </Box>
                <Typography variant="body2" color="text.secondary" sx={{mt: 1}}>
                    Спасибо! Любая поддержка помогает оплачивать инфраструктуру и развивать функциональность.
                </Typography>
            </Paper>

            <Paper sx={{p: 2}}>
                <Typography variant="subtitle1" fontWeight={700}>Контакты</Typography>
                <Typography variant="body1" sx={{mt: 1}}>
                    По вопросам и идеям: <Link href="/feedback">форма обратной связи</Link>.
                </Typography>
            </Paper>
        </Stack>
    );
}
