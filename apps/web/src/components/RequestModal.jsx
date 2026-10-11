import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCreateLoan } from '@/features/loans/hooks';
import { ApiError } from '@/lib/api';
import { formatDate, inclusiveDays, rangesOverlap, todayStr } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';

import { AvailabilityCalendar } from './AvailabilityCalendar';
import { Alert, Button, Input, Modal, Textarea, useToast } from './ui';

/** Screen 5: date range picker, message, summary and rules. */
export function RequestModal({ item, open, onClose }) {
  const navigate = useNavigate();
  const toast = useToast();
  const create = useCreateLoan(item.id);
  const [range, setRange] = useState({});
  const [message, setMessage] = useState('');
  const [error, setError] = useState(null);
  const today = todayStr();

  const days = range.start && range.end ? inclusiveDays(range.start, range.end) : 0;
  const clientError = (() => {
    if (!range.start || !range.end) return null;
    if (range.start < today) return 'Start date can’t be in the past.';
    if (range.end < range.start) return 'End date must be on or after the start date.';
    if (days > item.maxLoanDays) return `The owner allows at most ${item.maxLoanDays} days.`;
    if (item.availability.some((b) => rangesOverlap(range.start, range.end, b.startDate, b.endDate))) return 'Your dates include days when the item is already booked.';
    return null;
  })();

  const setDate = (key, v) => {
    setError(null);
    setRange((r) => ({ ...r, [key]: v || undefined }));
  };

  const submit = async () => {
    if (!range.start || !range.end || clientError) return;
    setError(null);
    try {
      const loan = await create.mutateAsync({ startDate: range.start, endDate: range.end, message: message.trim() || undefined });
      toast.success('Request sent! The owner has 48 hours to reply.');
      onClose();
      navigate(`/loans/${loan.id}`);
    } catch (err) {
      setError(errorMessage(err));
      if (err instanceof ApiError && err.code === 'DATES_UNAVAILABLE') setRange({});
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title={`Request ${item.name}`}
      description={`Pick up at ${item.pickupLocation}. Up to ${item.maxLoanDays} days.`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={submit} loading={create.isPending} disabled={!range.start || !range.end || !!clientError}>
            Send request
          </Button>
        </>
      }
    >
      <div className="grid gap-6 md:grid-cols-2">
        <div>
          <AvailabilityCalendar
            blocked={item.availability}
            value={range}
            maxDays={item.maxLoanDays}
            onChange={(r) => {
              setError(null);
              setRange(r);
            }}
            onInvalid={setError}
          />
          <div className="mt-4 grid grid-cols-2 gap-3">
            <Input label="Start date" type="date" min={today} value={range.start ?? ''} onChange={(e) => setDate('start', e.target.value)} required />
            <Input label="End date" type="date" min={range.start ?? today} value={range.end ?? ''} onChange={(e) => setDate('end', e.target.value)} required />
          </div>
        </div>
        <div className="space-y-4">
          <Textarea label="Message to the owner (optional)" placeholder="e.g. Need it for the physics lab on Monday." rows={3} maxLength={1000} value={message} onChange={(e) => setMessage(e.target.value)} />
          <div className="rounded-lg bg-gray-50 p-4 text-sm">
            <h3 className="font-semibold text-gray-900">Summary</h3>
            {range.start && range.end ? (
              <p className="mt-1 text-gray-800">
                {formatDate(range.start)} → {formatDate(range.end)} · <strong>{days} {days === 1 ? 'day' : 'days'}</strong>
              </p>
            ) : (
              <p className="mt-1 text-gray-600">Select a start and end date on the calendar.</p>
            )}
            {item.depositNote && <p className="mt-2 text-gray-800">Owner’s note: {item.depositNote}</p>}
            <h3 className="mt-3 font-semibold text-gray-900">Good to know</h3>
            <ul className="mt-1 list-disc space-y-1 pl-5 text-gray-700">
              <li>The owner has 48 hours to respond, or the request expires.</li>
              <li>Contact details are shared once the request is approved.</li>
              <li>You can request one extension later, if the dates are free.</li>
              <li>Overdue items block new requests until returned.</li>
            </ul>
          </div>
          {(error || clientError) && <Alert tone="error">{error ?? clientError}</Alert>}
        </div>
      </div>
    </Modal>
  );
}
