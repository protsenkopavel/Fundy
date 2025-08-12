import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';

export function useSearchParamsSync(obj: Record<string, string | undefined>) {
    const [sp, setSp] = useSearchParams();
    useEffect(() => {
        const next = new URLSearchParams(sp.toString());
        let changed = false;
        for (const [k,v] of Object.entries(obj)) {
            const cur = sp.get(k) ?? undefined;
            if (v !== cur) { changed = true; if (v == null) next.delete(k); else next.set(k, v); }
        }
        if (changed) setSp(next, { replace: true });
    }, [JSON.stringify(obj)]); // простая стабилизация
}
