import { Component, ReactNode } from 'react';
import { Box, Button, Typography } from '@mui/material';

type Props = { children: ReactNode };
type State = { error: Error | null };

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };
  static getDerivedStateFromError(error: Error) { return { error }; }
  render() {
    if (!this.state.error) return this.props.children;
    return (
      <Box sx={{p: 3}}>
        <Typography variant="h6" fontWeight={800}>Что-то пошло не так</Typography>
        <Typography variant="body2" sx={{mt: 1, whiteSpace: 'pre-wrap'}}>{String(this.state.error?.stack || this.state.error?.message)}</Typography>
        <Button sx={{mt: 2}} variant="outlined" onClick={() => location.reload()}>Перезагрузить</Button>
      </Box>
    );
  }
}
