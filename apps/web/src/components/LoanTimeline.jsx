import { formatDate, formatDateTime } from '@/lib/dates';
import { cn } from '@/lib/utils';
import PropTypes from 'prop-types';

function buildSteps(l) {
  const steps = [{ title: 'Requested', detail: formatDateTime(l.createdAt), state: 'done' }];
  const stopped = (title, detail) => [...steps, { title, detail, state: 'stopped'  }];

  if (l.status === 'REJECTED') return stopped('Declined', l.rejectionReason ?? undefined);
  if (l.status === 'EXPIRED') return stopped('Expired', 'The owner did not respond in time');
  if (l.status === 'CANCELLED' && !l.approvedAt) return stopped('Cancelled', l.cancellationReason ?? undefined);

  steps.push(
    l.approvedAt
      ? { title: 'Approved', detail: formatDateTime(l.approvedAt), state: 'done' }
      : { title: 'Waiting for the owner', detail: 'The owner has to approve or decline', state: 'current' },
  );
  if (l.status === 'CANCELLED') return stopped('Cancelled', l.cancellationReason ?? `Cancelled by the ${l.cancelledBy?.toLowerCase() ?? 'other party'}`);

  if (l.handedOverAt) steps.push({ title: 'Handed over', detail: formatDateTime(l.handedOverAt), state: 'done' });
  else if (l.handoverMarkedAt) steps.push({ title: 'Handover', detail: 'Owner marked it handed over. Waiting for the borrower to confirm.', state: 'current' });
  else steps.push({ title: 'Handover', detail: `From ${formatDate(l.startDate)}`, state: l.status === 'APPROVED' ? 'current' : 'upcoming' });

  const extended = l.endDate !== l.originalEndDate ? ' (extended)' : '';
  if (l.status === 'OVERDUE') steps.push({ title: 'Overdue', detail: `Was due ${formatDate(l.endDate)}`, state: 'stopped' });
  else steps.push({ title: `Due back${extended}`, detail: formatDate(l.endDate), state: l.returnedAt ? 'done' : l.status === 'ACTIVE' ? 'current' : 'upcoming' });

  if (l.returnedAt) steps.push({ title: 'Returned', detail: formatDateTime(l.returnedAt), state: 'done' });
  else if (l.returnMarkedAt)
    steps.push({ title: 'Return', detail: `The ${l.returnMarkedBy?.toLowerCase()} marked it returned. Waiting for confirmation.`, state: 'current' });
  else steps.push({ title: 'Returned', state: 'upcoming' });

  steps.push(
    l.completedAt
      ? { title: 'Completed', detail: formatDateTime(l.completedAt), state: 'done' }
      : { title: 'Completed', detail: l.status === 'RETURNED' ? 'After both reviews, or 7 days' : undefined, state: l.status === 'RETURNED' ? 'current' : 'upcoming' },
  );
  return steps;
}

const dot = {
  done: 'bg-green-700 text-white',
  current: 'bg-white text-brand-700 ring-2 ring-brand-700',
  upcoming: 'bg-white text-gray-400 ring-2 ring-gray-300',
  stopped: 'bg-red-700 text-white',
};
const glyph = { done: '✓', current: '•', upcoming: '', stopped: '✕' };
const stateLabel = { done: 'completed', current: 'in progress', upcoming: 'not started', stopped: 'stopped' };

export function LoanTimeline({ loan }) {
  const steps = buildSteps(loan);
  return (
    <ol className="relative space-y-5" aria-label="Loan timeline">
      {steps.map((s, i) => (
        <li key={i} className="relative flex gap-3" aria-current={s.state === 'current' ? 'step' : undefined}>
          {i < steps.length - 1 && <span className="absolute left-3 top-7 h-[calc(100%-4px)] w-0.5 bg-gray-200" aria-hidden />}
          <span className={cn('relative z-10 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold', dot[s.state])} aria-hidden>
            {glyph[s.state]}
          </span>
          <div>
            <p className={cn('text-sm font-medium', s.state === 'upcoming' ? 'text-gray-600' : 'text-gray-900')}>
              {s.title}
              <span className="sr-only"> ({stateLabel[s.state]})</span>
            </p>
            {s.detail && <p className="text-xs text-gray-600">{s.detail}</p>}
          </div>
        </li>
      ))}
    </ol>
  );
}

LoanTimeline.propTypes = { loan: PropTypes.object.isRequired };
