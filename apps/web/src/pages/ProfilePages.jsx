import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ContactPreference } from '@/lib/constants';
import { PageContainer } from '@/components/Layout';
import { ReportDialog } from '@/components/ReportDialog';
import { Alert, Avatar, Button, Card, EmptyState, ErrorState, Input, Pagination, RatingStars, Select, Skeleton, useConfirm, useToast } from '@/components/ui';
import { useAuth } from '@/features/auth/AuthProvider';
import { useDeleteMe, useProfile, useUpdateMe, useUserReviews } from '@/features/users/hooks';
import { setSession } from '@/lib/api';
import { formatDate } from '@/lib/dates';
import { errorMessage, fieldErrors } from '@/lib/errorMessages';

function ProfileHeader({ profile, extra }) {
  const stats = [
    { label: 'Items listed', value: profile.stats.itemsListed },
    { label: 'Completed loans', value: profile.stats.completedLoans },
    { label: 'As lender', value: profile.stats.completedAsLender },
    { label: 'As borrower', value: profile.stats.completedAsBorrower },
  ];
  return (
    <Card>
      <div className="flex flex-col items-center gap-4 text-center sm:flex-row sm:text-left">
        <Avatar name={profile.name} url={profile.avatarUrl} size="xl" />
        <div className="flex-1">
          <h1 className="text-2xl font-bold text-gray-900">{profile.name}</h1>
          <p className="text-gray-700">{profile.department}</p>
          <RatingStars value={profile.ratingAvg} count={profile.ratingCount} className="mt-1" />
          <p className="mt-1 text-xs text-gray-600">Member since {formatDate(profile.memberSince, { month: 'long', year: 'numeric' })}</p>
        </div>
        {extra}
      </div>
      <dl className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {stats.map((s) => (
          <div key={s.label} className="rounded-lg bg-gray-50 p-3 text-center">
            <dt className="text-xs text-gray-600">{s.label}</dt>
            <dd className="text-xl font-bold text-gray-900">{s.value}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}

function ReviewsList({ userId }) {
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('reviews') ?? 1);
  const { data, isLoading } = useUserReviews(userId, page);
  return (
    <Card>
      <h2 className="mb-3 text-lg font-semibold text-gray-900">Reviews</h2>
      {isLoading ? (
        <Skeleton className="h-24" />
      ) : !data?.data.length ? (
        <EmptyState icon="⭐" title="No reviews yet" message="Reviews appear after completed loans." />
      ) : (
        <>
          <ul className="divide-y divide-gray-100">
            {data.data.map((r) => (
              <li key={r.id} className="flex gap-3 py-3">
                <Avatar name={r.reviewer.name} url={r.reviewer.avatarUrl} size="sm" />
                <div>
                  <p className="text-sm text-gray-900">
                    <span className="font-medium">{r.reviewer.name}</span>
                    <span className="text-gray-600">
                      {' '}
                      · as {r.reviewerRole === 'BORROWER' ? 'borrower' : 'lender'} of {r.item.name} · {formatDate(r.createdAt)}
                    </span>
                  </p>
                  <RatingStars value={r.rating} />
                  {r.comment && <p className="mt-1 text-sm text-gray-800">{r.comment}</p>}
                </div>
              </li>
            ))}
          </ul>
          <Pagination page={data.meta.page} totalPages={data.meta.totalPages} onChange={(p) => setParams({ reviews: String(p) })} />
        </>
      )}
    </Card>
  );
}

/** Screen 11 (public view). */
export function UserProfilePage() {
  const { id = '' } = useParams();
  const { user, status } = useAuth();
  const { data, isLoading, isError, error, refetch } = useProfile(id);
  const [reportOpen, setReportOpen] = useState(false);
  if (isLoading)
    return (
      <PageContainer>
        <Skeleton className="h-48" />
      </PageContainer>
    );
  if (isError || !data)
    return (
      <PageContainer>
        <ErrorState error={error} onRetry={() => refetch()} />
      </PageContainer>
    );
  const canReport = status === 'authenticated' && user?.id !== id;
  return (
    <PageContainer>
      <div className="space-y-6">
        <ProfileHeader
          profile={data}
          extra={
            canReport && (
              <Button variant="ghost" size="sm" onClick={() => setReportOpen(true)}>
                Report user
              </Button>
            )
          }
        />
        <ReviewsList userId={id} />
      </div>
      <ReportDialog targetType="USER" targetId={id} targetName={data.name} open={reportOpen} onClose={() => setReportOpen(false)} />
    </PageContainer>
  );
}

/** Screen 11 (own profile): stats, reviews, edit form. */
export function MyProfilePage() {
  const { user, setUser } = useAuth();
  const { data: profile, isLoading } = useProfile(user?.id);
  const update = useUpdateMe();
  const del = useDeleteMe();
  const confirm = useConfirm();
  const toast = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', department: '', phone: '', contactPreference: 'EMAIL' });
  const [avatar, setAvatar] = useState(null);
  const [removeAvatar, setRemoveAvatar] = useState(false);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const preview = useMemo(() => (avatar ? URL.createObjectURL(avatar) : null), [avatar]);
  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview);
  }, [preview]);

  useEffect(() => {
    if (user) setForm({ name: user.name, department: user.department, phone: user.phone ?? '', contactPreference: user.contactPreference });
  }, [user]);

  if (!user) return null;
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const save = async (e) => {
    e.preventDefault();
    setError(null);
    setErrors({});
    const fd = new FormData();
    Object.entries(form).forEach(([k, v]) => fd.append(k, v));
    if (avatar) fd.append('avatar', avatar);
    else if (removeAvatar) fd.append('removeAvatar', 'true');
    try {
      const me = await update.mutateAsync(fd);
      setUser(me);
      setAvatar(null);
      setRemoveAvatar(false);
      toast.success('Profile updated');
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    }
  };

  const deleteAccount = async () => {
    const r = await confirm({
      title: 'Delete your account?',
      message: 'Your profile is anonymised, your items are unlisted and pending requests are cancelled. This can’t be undone. You can’t delete your account while you have approved or active loans.',
      danger: true,
      confirmLabel: 'Delete my account',
    });
    if (!r.confirmed) return;
    try {
      await del.mutateAsync();
      setSession(null, null);
      navigate('/');
      toast.success('Your account was deleted.');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const avatarUrl = preview ?? (removeAvatar ? null : user.avatarUrl);

  return (
    <PageContainer>
      <div className="space-y-6">
        {isLoading || !profile ? <Skeleton className="h-48" /> : <ProfileHeader profile={profile} />}

        <Card>
          <h2 className="mb-4 text-lg font-semibold text-gray-900">Edit profile</h2>
          <form onSubmit={save} className="space-y-4" noValidate>
            <div className="flex items-center gap-4">
              <Avatar name={user.name} url={avatarUrl} size="lg" />
              <div className="flex flex-wrap gap-2">
                <label className="inline-flex min-h-[36px] cursor-pointer items-center rounded-lg border border-gray-300 bg-white px-3 text-sm font-medium text-gray-900 focus-within:outline focus-within:outline-2 focus-within:outline-brand-700 hover:bg-gray-50">
                  Change photo
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    className="sr-only"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f && f.size > 5 * 1024 * 1024) return toast.error('Photos must be 5 MB or smaller.');
                      setAvatar(f ?? null);
                      setRemoveAvatar(false);
                    }}
                  />
                </label>
                {(user.avatarUrl || avatar) && (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      setAvatar(null);
                      setRemoveAvatar(true);
                    }}
                  >
                    Remove photo
                  </Button>
                )}
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input label="Name" required value={form.name} onChange={set('name')} error={errors.name} />
              <Input label="Department" required value={form.department} onChange={set('department')} error={errors.department} />
              <Input label="Phone" type="tel" value={form.phone} onChange={set('phone')} error={errors.phone} hint="Optional" />
              <Select label="Preferred contact" value={form.contactPreference} onChange={set('contactPreference')} hint="Shared only with the other person on an approved loan.">
                {ContactPreference.map((c) => (
                  <option key={c} value={c}>
                    {c === 'EMAIL' ? 'Email only' : c === 'PHONE' ? 'Phone' : 'Email and phone'}
                  </option>
                ))}
              </Select>
            </div>
            <p className="text-sm text-gray-600">Email: {user.email} {user.emailVerified ? '(verified)' : '(not verified)'}</p>
            {error && <Alert tone="error">{error}</Alert>}
            <div className="flex justify-end">
              <Button type="submit" loading={update.isPending}>
                Save changes
              </Button>
            </div>
          </form>
        </Card>

        <ReviewsList userId={user.id} />

        <Card className="border-red-200">
          <h2 className="text-lg font-semibold text-red-900">Delete account</h2>
          <p className="mt-1 text-sm text-gray-700">Permanently remove your personal details from BorrowBox.</p>
          <Button variant="danger" className="mt-4" onClick={deleteAccount} loading={del.isPending}>
            Delete my account
          </Button>
        </Card>
      </div>
    </PageContainer>
  );
}
