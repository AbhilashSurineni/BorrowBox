import { Link, useParams } from 'react-router-dom';
import { ItemImage } from '@/components/ItemCard';
import { LoanActions } from '@/components/LoanActions';
import { LoanTimeline } from '@/components/LoanTimeline';
import { PageContainer } from '@/components/Layout';
import { UserChip } from '@/components/UserChip';
import { Alert, Card, ErrorState, LoanStatusChip, Skeleton } from '@/components/ui';
import { useLoan } from '@/features/loans/hooks';
import { formatDate, formatRange, inclusiveDays } from '@/lib/dates';

function nextStepHint(l) {
  const owner = l.viewerRole === 'OWNER';
  switch (l.status) {
    case 'PENDING':
      return owner ? 'Approve or decline this request. It expires after 48 hours without a reply.' : 'Waiting for the owner to respond (up to 48 hours).';
    case 'APPROVED':
      if (!l.handoverMarkedAt) return owner ? `Meet ${l.borrower.name} and mark the item as handed over from ${formatDate(l.startDate)}.` : `Arrange pickup with ${l.owner.name}. They’ll mark it handed over.`;
      return owner ? 'Waiting for the borrower to confirm they received it.' : 'Confirm you received the item.';
    case 'ACTIVE':
    case 'OVERDUE':
      if (!l.returnMarkedBy) return `Due back on ${formatDate(l.endDate)}. Either of you can mark it returned; the other confirms.`;
      return (l.returnMarkedBy === 'OWNER') === owner ? 'Waiting for the other person to confirm the return.' : 'Please confirm the return.';
    case 'RETURNED':
      return (owner ? l.reviewedBy.owner : l.reviewedBy.borrower) ? 'Thanks for your review!' : 'Leave a review to complete the loan.';
    default:
      return null;
  }
}

/** Screen 9: timeline, contact info after approval, handover/return buttons, extension request. */
export function LoanDetailPage() {
  const { id = '' } = useParams();
  const { data: loan, isLoading, isError, error, refetch } = useLoan(id);

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="mt-4 h-40 w-full" />
      </PageContainer>
    );
  }
  if (isError || !loan) {
    return (
      <PageContainer>
        <ErrorState error={error} onRetry={() => refetch()} />
      </PageContainer>
    );
  }

  const owner = loan.viewerRole === 'OWNER';
  const counterparty = owner ? loan.borrower : loan.owner;
  const hint = nextStepHint(loan);
  const contact = loan.counterpartyContact;

  return (
    <PageContainer>
      <nav aria-label="Breadcrumb" className="mb-4 text-sm text-gray-600">
        <Link to={`/my/loans?role=${owner ? 'owner' : 'borrower'}`} className="hover:underline">
          My loans
        </Link>{' '}
        / {loan.item.name}
      </nav>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <ItemImage url={loan.item.coverImageUrl} name={loan.item.name} className="h-24 w-24 shrink-0 rounded-xl" />
        <div className="flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold text-gray-900">
              <Link to={`/items/${loan.item.id}`} className="hover:underline">
                {loan.item.name}
              </Link>
            </h1>
            <LoanStatusChip status={loan.status} />
          </div>
          <p className="mt-1 text-gray-800">
            {formatRange(loan.startDate, loan.endDate)} · {inclusiveDays(loan.startDate, loan.endDate)} days
          </p>
          <p className="text-sm text-gray-600">Pickup: {loan.item.pickupLocation}</p>
        </div>
      </div>

      {loan.status === 'OVERDUE' && (
        <Alert tone="error" className="mt-4">
          This loan is overdue since {formatDate(loan.endDate)}. {owner ? 'We’re sending the borrower daily reminders.' : 'Please return the item as soon as possible. New requests are blocked until then.'}
        </Alert>
      )}
      {hint && (
        <Alert tone="info" className="mt-4">
          <strong>Next step:</strong> {hint}
        </Alert>
      )}

      <div className="mt-6 grid gap-6 md:grid-cols-[1fr_320px]">
        <div className="space-y-6">
          <Card>
            <h2 className="mb-4 text-lg font-semibold text-gray-900">Timeline</h2>
            <LoanTimeline loan={loan} />
          </Card>
          {(loan.message || loan.rejectionReason || loan.cancellationReason || loan.returnConditionNote || loan.extension.status !== 'NONE') && (
            <Card>
              <h2 className="mb-3 text-lg font-semibold text-gray-900">Notes</h2>
              <dl className="space-y-3 text-sm">
                {loan.message && (
                  <div>
                    <dt className="text-gray-600">Borrower’s message</dt>
                    <dd className="text-gray-900">“{loan.message}”</dd>
                  </div>
                )}
                {loan.rejectionReason && (
                  <div>
                    <dt className="text-gray-600">Reason for declining</dt>
                    <dd className="text-gray-900">{loan.rejectionReason}</dd>
                  </div>
                )}
                {loan.cancellationReason && (
                  <div>
                    <dt className="text-gray-600">Cancellation reason</dt>
                    <dd className="text-gray-900">{loan.cancellationReason}</dd>
                  </div>
                )}
                {loan.returnConditionNote && (
                  <div>
                    <dt className="text-gray-600">Condition on return</dt>
                    <dd className="text-gray-900">{loan.returnConditionNote}</dd>
                  </div>
                )}
                {loan.extension.status !== 'NONE' && (
                  <div>
                    <dt className="text-gray-600">Extension</dt>
                    <dd className="text-gray-900">
                      {loan.extension.status.charAt(0) + loan.extension.status.slice(1).toLowerCase()} · until {formatDate(loan.extension.requestedUntil)}
                    </dd>
                  </div>
                )}
              </dl>
            </Card>
          )}
        </div>
        <div className="space-y-6">
          <Card>
            <UserChip user={counterparty} label={owner ? 'Borrower' : 'Owner'} />
            <div className="mt-4 border-t border-gray-100 pt-4 text-sm">
              <h2 className="font-semibold text-gray-900">Contact</h2>
              {contact ? (
                <ul className="mt-2 space-y-1">
                  <li>
                    ✉️{' '}
                    <a href={`mailto:${contact.email}`} className="text-brand-700 underline">
                      {contact.email}
                    </a>
                  </li>
                  {contact.phone && (
                    <li>
                      📞{' '}
                      <a href={`tel:${contact.phone}`} className="text-brand-700 underline">
                        {contact.phone}
                      </a>
                    </li>
                  )}
                </ul>
              ) : (
                <p className="mt-1 text-gray-600">Contact details are shared once the request is approved.</p>
              )}
            </div>
          </Card>
          <div className="hidden md:block">
            <LoanActions loan={loan} className="flex-col [&>button]:w-full" />
          </div>
        </div>
      </div>

      {/* Mobile: primary actions always visible above the bottom nav */}
      {loan.availableActions.length > 0 && (
        <>
          <div className="fixed inset-x-0 bottom-[56px] z-20 max-h-[50vh] overflow-y-auto border-t border-gray-200 bg-white p-3 md:hidden">
            <LoanActions loan={loan} className="[&>button]:flex-1" />
          </div>
          <div className="h-28 md:hidden" aria-hidden />
        </>
      )}
    </PageContainer>
  );
}
