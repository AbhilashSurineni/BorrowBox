import { createContext, useCallback, useContext, useRef, useState } from 'react';
import { Button } from './Button';
import { Textarea } from './Field';
import { Modal } from './Modal';

const ConfirmContext = createContext(null);

/** Promise-based confirm dialog for destructive or irreversible actions. */
export function ConfirmProvider({ children }) {
  const [opts, setOpts] = useState(null);
  const [value, setValue] = useState('');
  const [touched, setTouched] = useState(false);
  const resolver = useRef();

  const confirm = useCallback((o) => {
    setOpts(o);
    setValue('');
    setTouched(false);
    return new Promise((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  const close = (confirmed) => {
    if (confirmed && opts?.input?.required && !value.trim()) {
      setTouched(true);
      return;
    }
    resolver.current?.({ confirmed, value: value.trim() });
    setOpts(null);
  };

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Modal
        open={!!opts}
        onClose={() => close(false)}
        title={opts?.title ?? ''}
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => close(false)}>
              {opts?.cancelLabel ?? 'Cancel'}
            </Button>
            <Button variant={opts?.danger ? 'danger' : 'primary'} onClick={() => close(true)} data-autofocus={opts?.input ? undefined : true}>
              {opts?.confirmLabel ?? 'Confirm'}
            </Button>
          </>
        }
      >
        {opts?.message && <div className="text-sm text-gray-700">{opts.message}</div>}
        {opts?.input && (
          <Textarea
            data-autofocus
            wrapperClassName="mt-4"
            label={opts.input.label}
            required={opts.input.required}
            placeholder={opts.input.placeholder}
            maxLength={opts.input.maxLength ?? 500}
            rows={3}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            error={touched && opts.input.required && !value.trim() ? 'This field is required' : undefined}
          />
        )}
      </Modal>
    </ConfirmContext.Provider>
  );
}

export function useConfirm() {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error('useConfirm must be used inside ConfirmProvider');
  return ctx;
}
