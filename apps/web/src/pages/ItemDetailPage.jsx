import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AvailabilityCalendar } from '@/components/AvailabilityCalendar';
import { ItemImage } from '@/components/ItemCard';
import { PageContainer } from '@/components/Layout';
import { ReportDialog } from '@/components/ReportDialog';
import { RequestModal } from '@/components/RequestModal';
import { UserChip } from '@/components/UserChip';
import { Alert, Avatar, Button, ButtonLink, Card, ErrorState, ItemStatusChip, RatingStars, Skeleton, useConfirm, useToast } from '@/components/ui';
import { useAuth } from '@/features/auth/AuthProvider';
import { useDeleteItem, useItem, useTogglePause } from '@/features/items/hooks';
import { formatDate } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';
import { CONDITION_LABELS } from '@/lib/utils';

/** Screen 4: gallery, details, owner card, availability calendar, request button, reviews, report link. */
export function ItemDetailPage() {
  const { id = '' } = useParams();
  const { user, status } = useAuth();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const toast = useToast();
  const { data: item, isLoading, isError, error, refetch } = useItem(id);
  const pause = useTogglePause();
  const remove = useDeleteItem();
  const [active, setActive] = useState(0);
  const [requestOpen, setRequestOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);

  if (isLoading) {
    return (
      <PageContainer wide>
        <div className="grid gap-8 md:grid-cols-2">
          <Skeleton className="aspect-[4/3] w-full" />
          <div className="space-y-3">
            <Skeleton className="h-8 w-2/3" />
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-24 w-full" />
          </div>
        </div>
      </PageContainer>
    );
  }
  if (isError || !item) {
    return (
      <PageContainer>
        <ErrorState error={error} onRetry={() => refetch()} />
      </PageContainer>
    );
  }

  const isOwner = user?.id === item.owner.id;
  const canRequest = !isOwner && !item.isPaused && !item.isRemoved;
  const image = item.images[active];

  const onRequest = () => {
    if (status !== 'authenticated') return navigate('/login', { state: { from: `/items/${id}` } });
    if (!user?.emailVerified) return toast.error('Verify your email address before requesting items.');
    setRequestOpen(true);
  };

  const onPause = async () => {
    try {
      await pause.mutateAsync(item.id);
      toast.success(item.isPaused ? 'Item is visible again' : 'Item paused. No new requests until you resume it.');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const onDelete = async () => {
    const r = await confirm({ title: 'Delete this item?', message: 'Pending requests will be declined. Items with approved or active loans can’t be deleted.', danger: true, confirmLabel: 'Delete item' });
    if (!r.confirmed) return;
    try {
      await remove.mutateAsync(item.id);
      toast.success('Item deleted');
      navigate('/my/items');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <PageContainer wide>
      <nav aria-label="Breadcrumb" className="mb-4 text-sm text-gray-600">
        <Link to="/browse" className="hover:underline">
          Browse
        </Link>{' '}
        /{' '}
        <Link to={`/browse?category=${item.category.id}`} className="hover:underline">
          {item.category.name}
        </Link>
      </nav>
      {item.isRemoved && <Alert tone="error" className="mb-4">This item was removed by a moderator{item.removedReason ? `: ${item.removedReason}` : '.'}</Alert>}
      <div className="grid gap-8 md:grid-cols-2">
        <div>
          {image ? (
            <img src={image.url} alt={`${item.name}, photo ${active + 1} of ${item.images.length}`} className="aspect-[4/3] w-full rounded-xl object-cover" />
          ) : (
            <ItemImage url={null} name={item.name} className="aspect-[4/3] w-full rounded-xl" />
          )}
          {item.images.length > 1 && (
            <ul className="mt-3 flex gap-2 overflow-x-auto" aria-label="Photos">
              {item.images.map((img, i) => (
                <li key={img.id}>
                  <button
                    type="button"
                    onClick={() => setActive(i)}
                    aria-label={`Show photo ${i + 1}`}
                    aria-pressed={i === active}
                    className={`h-16 w-16 overflow-hidden rounded-lg ring-2 ${i === active ? 'ring-brand-700' : 'ring-transparent'}`}
                  >
                    <img src={img.url} alt="" className="h-full w-full object-cover" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="space-y-5">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <ItemStatusChip status={item.status} />
              <span className="text-sm text-gray-600">
                {item.category.name} · {CONDITION_LABELS[item.condition]}
              </span>
            </div>
            <h1 className="mt-2 text-2xl font-bold text-gray-900 sm:text-3xl">{item.name}</h1>
            <RatingStars value={item.ratingAvg} count={item.ratingCount} className="mt-1" />
          </div>
          <p className="whitespace-pre-line text-gray-800">{item.description}</p>
          <dl className="grid grid-cols-2 gap-3 rounded-xl bg-white p-4 text-sm ring-1 ring-gray-200">
            <div>
              <dt className="text-gray-600">Pickup</dt>
              <dd className="font-medium text-gray-900">{item.pickupLocation}</dd>
            </div>
            <div>
              <dt className="text-gray-600">Max loan</dt>
              <dd className="font-medium text-gray-900">{item.maxLoanDays} days</dd>
            </div>
            {item.depositNote && (
              <div className="col-span-2">
                <dt className="text-gray-600">Owner’s note</dt>
                <dd className="font-medium text-gray-900">{item.depositNote}</dd>
              </div>
            )}
          </dl>
          <Card>
            <UserChip user={item.owner} label="Lent by" />
          </Card>

          {isOwner ? (
            <div className="flex flex-wrap gap-2">
              <ButtonLink to={`/items/${item.id}/edit`} variant="secondary">
                Edit
              </ButtonLink>
              {!item.isRemoved && (
                <Button variant="secondary" onClick={onPause} loading={pause.isPending}>
                  {item.isPaused ? 'Resume listing' : 'Pause listing'}
                </Button>
              )}
              <Button variant="danger" onClick={onDelete} loading={remove.isPending}>
                Delete
              </Button>
            </div>
          ) : (
            <div className="hidden md:block">
              <Button size="lg" className="w-full" disabled={!canRequest} onClick={onRequest}>
                {item.isPaused ? 'Temporarily unavailable' : 'Request to borrow'}
              </Button>
            </div>
          )}
        </div>
      </div>

      <div className="mt-10 grid gap-8 md:grid-cols-2">
        <Card>
          <h2 className="mb-3 text-lg font-semibold text-gray-900">Availability</h2>
          <AvailabilityCalendar blocked={item.availability} />
        </Card>
        <Card>
          <h2 className="mb-3 text-lg font-semibold text-gray-900">Reviews</h2>
          {item.reviews.length === 0 ? (
            <p className="text-sm text-gray-600">No reviews for this item yet.</p>
          ) : (
            <ul className="space-y-4">
              {item.reviews.map((r) => (
                <li key={r.id} className="flex gap-3">
                  <Avatar name={r.reviewer.name} url={r.reviewer.avatarUrl} size="sm" />
                  <div>
                    <p className="text-sm font-medium text-gray-900">
                      {r.reviewer.name} <span className="font-normal text-gray-600">· {formatDate(r.createdAt)}</span>
                    </p>
                    <RatingStars value={r.rating} />
                    {r.comment && <p className="mt-1 text-sm text-gray-800">{r.comment}</p>}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {!isOwner && status === 'authenticated' && (
        <p className="mt-8 text-center text-sm">
          <button type="button" className="font-medium text-gray-700 underline hover:text-gray-900" onClick={() => setReportOpen(true)}>
            Report this item
          </button>
        </p>
      )}

      {/* Mobile: primary action always visible */}
      {!isOwner && (
        <div className="fixed inset-x-0 bottom-[56px] z-20 border-t border-gray-200 bg-white p-3 md:hidden" style={status !== 'authenticated' ? { bottom: 0 } : undefined}>
          <Button size="lg" className="w-full" disabled={!canRequest} onClick={onRequest}>
            {item.isPaused ? 'Temporarily unavailable' : 'Request to borrow'}
          </Button>
        </div>
      )}
      <div className="h-20 md:hidden" aria-hidden />

      {requestOpen && <RequestModal item={item} open={requestOpen} onClose={() => setRequestOpen(false)} />}
      <ReportDialog targetType="ITEM" targetId={item.id} targetName={item.name} open={reportOpen} onClose={() => setReportOpen(false)} />
    </PageContainer>
  );
}
