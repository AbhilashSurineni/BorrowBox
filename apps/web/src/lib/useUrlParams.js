import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/** Reads/writes query-string state; changing any filter resets `page`. */
export function useUrlParams() {
  const [params, setParams] = useSearchParams();
  const get = useCallback((k) => params.get(k) ?? undefined, [params]);
  const set = useCallback(
    (patch) => {
      const next = new URLSearchParams(params);
      for (const [k, v] of Object.entries(patch)) {
        if (v) next.set(k, v);
        else next.delete(k);
      }
      if (!('page' in patch)) next.delete('page');
      setParams(next, { replace: true });
    },
    [params, setParams],
  );
  return { get, set, page: Number(params.get('page') ?? 1) };
}
