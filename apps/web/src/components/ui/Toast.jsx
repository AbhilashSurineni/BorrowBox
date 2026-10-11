import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { cn } from '@/lib/utils';

const ToastContext = createContext(null);
let nextId = 1;

const tones = {
  success: 'border-green-700 bg-green-50 text-green-900',
  error: 'border-red-700 bg-red-50 text-red-900',
  info: 'border-brand-700 bg-white text-gray-900',
};
const icons = { success: '✓', error: '!', info: 'i' };

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const push = useCallback(
    (tone, message) => {
      const id = nextId++;
      setToasts((t) => [...t.slice(-3), { id, tone, message }]);
      setTimeout(() => dismiss(id), tone === 'error' ? 6000 : 4000);
    },
    [dismiss],
  );
  const api = useMemo(
    () => ({ success: (m) => push('success', m), error: (m) => push('error', m), info: (m) => push('info', m) }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="pointer-events-none fixed inset-x-0 bottom-20 z-[60] flex flex-col items-center gap-2 px-4 sm:bottom-6 sm:items-end"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.tone === 'error' ? 'alert' : 'status'}
            className={cn('pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-lg border-l-4 px-4 py-3 shadow-lg animate-slide-in', tones[t.tone])}
          >
            <span aria-hidden className="mt-0.5 font-bold">
              {icons[t.tone]}
            </span>
            <p className="flex-1 text-sm">{t.message}</p>
            <button type="button" onClick={() => dismiss(t.id)} className="text-sm font-medium underline" aria-label="Dismiss notification">
              ✕
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return ctx;
}
