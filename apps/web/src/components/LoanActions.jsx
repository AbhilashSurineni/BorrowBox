import { useState } from 'react';
import { useCreateReview, useLoanCommand } from '@/features/loans/hooks';
import { addDays, formatDate } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';

import { Button, Input, Modal, RatingInput, Textarea, useConfirm, useToast } from './ui';
import PropTypes from 'prop-types';

/** Buttons driven by the server's availableActions for the viewer's role and loan status. */
export function LoanActions({ loan, size = 'md', className = '' }) {
  const confirm = useConfirm();
  const toast = useToast();
  const command = useLoanCommand(loan.id);
  const [extensionOpen, setExtensionOpen] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const isOwner = loan.viewerRole === 'OWNER';
  const actions = loan.availableActions;
  const other = isOwner ? loan.borrower.name : loan.owner.name;

  const run = async (cmd, success) => {
    try {
      await command.mutateAsync(cmd);
      toast.success(success);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const handlers = {
    approve: {
      label: 'Approve',
      variant: 'success',
      onClick: async () => {
        const r = await confirm({
          title: 'Approve this request?',
          message: `${loan.borrower.name} will see your contact details. Other pending requests for overlapping dates will be declined automatically.`,
          confirmLabel: 'Approve',
        });
        if (r.confirmed) await run({ kind: 'approve' }, 'Request approved');
      },
    },
    reject: {
      label: 'Decline',
      variant: 'secondary',
      onClick: async () => {
        const r = await confirm({ title: 'Decline this request?', danger: true, confirmLabel: 'Decline', input: { label: 'Reason (shared with the borrower)', required: true } });
        if (r.confirmed) await run({ kind: 'reject', reason: r.value }, 'Request declined');
      },
    },
    cancel: {
      label: loan.status === 'PENDING' ? 'Cancel request' : 'Cancel loan',
      variant: 'danger',
      onClick: async () => {
        const r = await confirm({
          title: loan.status === 'PENDING' ? 'Cancel your request?' : 'Cancel this loan?',
          message: `${other} will be notified. This can’t be undone.`,
          danger: true,
          confirmLabel: 'Yes, cancel',
          cancelLabel: 'Keep it',
          input: { label: 'Reason (optional)' },
        });
        if (r.confirmed) await run({ kind: 'cancel', reason: r.value || undefined }, 'Cancelled');
      },
    },
    handover: {
      label: 'Mark as handed over',
      onClick: async () => {
        const r = await confirm({ title: 'Mark as handed over?', message: `${loan.borrower.name} will be asked to confirm they received the item.`, confirmLabel: 'Mark handed over' });
        if (r.confirmed) await run({ kind: 'handover' }, 'Marked as handed over. Waiting for the borrower to confirm.');
      },
    },
    confirm_handover: {
      label: 'Confirm I received it',
      onClick: async () => {
        const r = await confirm({ title: 'Confirm receipt?', message: `Confirm that you received ${loan.item.name}. It’s due back on ${formatDate(loan.endDate)}.`, confirmLabel: 'Confirm receipt' });
        if (r.confirmed) await run({ kind: 'handover' }, 'Enjoy! The loan is now active.');
      },
    },
    mark_returned: {
      label: 'Mark as returned',
      onClick: async () => {
        const r = await confirm({
          title: 'Mark as returned?',
          message: `${other} will be asked to confirm the return.`,
          confirmLabel: 'Mark returned',
          input: isOwner ? { label: 'Condition on return (optional)', placeholder: 'e.g. Same condition as lent' } : undefined,
        });
        if (r.confirmed) await run({ kind: 'return', conditionNote: r.value || undefined }, 'Marked as returned. Waiting for confirmation.');
      },
    },
    confirm_return: {
      label: 'Confirm return',
      variant: 'success',
      onClick: async () => {
        const r = await confirm({
          title: 'Confirm the item was returned?',
          confirmLabel: 'Confirm return',
          input: isOwner ? { label: 'Condition on return (optional)', placeholder: 'e.g. Small scratch on the cover' } : undefined,
        });
        if (r.confirmed) await run({ kind: 'return', conditionNote: r.value || undefined }, 'Return confirmed. You can now leave a review.');
      },
    },
    request_extension: { label: 'Request extension', variant: 'secondary', onClick: () => setExtensionOpen(true) },
    review: { label: 'Leave a review', onClick: () => setReviewOpen(true) },
  };

  const decide = actions.includes('decide_extension') && loan.extension.requestedUntil;
  const visible = actions.filter((a) => handlers[a]);
  if (!visible.length && !decide) return null;

  return (
    <div className={`flex flex-wrap gap-2 ${className}`}>
      {decide && (
        <div className="w-full rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">
          <p>
            {loan.borrower.name} asked to keep it until <strong>{formatDate(loan.extension.requestedUntil)}</strong>.
            {loan.extension.message && <span className="block italic">“{loan.extension.message}”</span>}
          </p>
          <div className="mt-2 flex gap-2">
            <Button size="sm" variant="success" loading={command.isPending} onClick={() => run({ kind: 'extension-decision', approve: true }, 'Extension approved')}>
              Approve extension
            </Button>
            <Button size="sm" variant="secondary" disabled={command.isPending} onClick={() => run({ kind: 'extension-decision', approve: false }, 'Extension declined')}>
              Decline
            </Button>
          </div>
        </div>
      )}
      {visible.map((a, i) => {
        const h = handlers[a];
        return (
          <Button key={a} size={size} variant={h.variant ?? (i === 0 ? 'primary' : 'secondary')} disabled={command.isPending} onClick={h.onClick}>
            {h.label}
          </Button>
        );
      })}
      <ExtensionModal loan={loan} open={extensionOpen} onClose={() => setExtensionOpen(false)} onSubmit={(until, message) => run({ kind: 'extension', until, message }, 'Extension requested')} />
      <ReviewModal loan={loan} open={reviewOpen} onClose={() => setReviewOpen(false)} />
    </div>
  );
}

function ExtensionModal({ loan, open, onClose, onSubmit }) {
  const min = addDays(loan.endDate, 1);
  const max = addDays(loan.endDate, loan.item.maxLoanDays);
  const [until, setUntil] = useState(min);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setBusy(true);
    await onSubmit(until, message.trim() || undefined);
    setBusy(false);
    onClose();
  };
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Request an extension"
      description={`You can request one extension per loan. It’s currently due on ${formatDate(loan.endDate)}.`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={busy} onClick={submit} disabled={!until || until < min || until > max}>
            Send request
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Input label="Keep it until" type="date" min={min} max={max} value={until} onChange={(e) => setUntil(e.target.value)} hint={`Up to ${formatDate(max)}`} required />
        <Textarea label="Message to the owner (optional)" rows={3} maxLength={500} value={message} onChange={(e) => setMessage(e.target.value)} />
      </div>
    </Modal>
  );
}

function ReviewModal({ loan, open, onClose }) {
  const toast = useToast();
  const review = useCreateReview(loan.id);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const target = loan.viewerRole === 'OWNER' ? loan.borrower.name : loan.owner.name;
  const submit = async () => {
    try {
      await review.mutateAsync({ rating, comment: comment.trim() || undefined });
      toast.success('Thanks for your review!');
      onClose();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Review ${target}`}
      description="Reviews are public and can only be submitted once."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={review.isPending} disabled={!rating} onClick={submit}>
            Submit review
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <RatingInput value={rating} onChange={setRating} label="How did it go?" />
        <Textarea label="Comment (optional)" rows={3} maxLength={1000} value={comment} onChange={(e) => setComment(e.target.value)} />
      </div>
    </Modal>
  );
}

LoanActions.propTypes = { loan: PropTypes.object.isRequired, size: PropTypes.oneOf(['sm', 'md']), className: PropTypes.string };
