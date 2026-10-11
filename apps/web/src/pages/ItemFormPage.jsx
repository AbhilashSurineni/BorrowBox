import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ItemCondition } from '@/lib/constants';
import { validateItem } from '@/lib/validators';
import { ImageUploader } from '@/components/ImageUploader';
import { PageContainer } from '@/components/Layout';
import { Alert, Button, Card, ErrorState, Input, PageHeader, PageSpinner, Select, Textarea, useToast } from '@/components/ui';
import { useAuth } from '@/features/auth/AuthProvider';
import { useCategories, useItem, useSaveItem } from '@/features/items/hooks';
import { errorMessage, fieldErrors } from '@/lib/errorMessages';
import { CONDITION_LABELS } from '@/lib/utils';

const empty = { name: '', categoryId: '', description: '', condition: 'GOOD', maxLoanDays: '14', pickupLocation: '', depositNote: '' };

/** Screen 6: add / edit item with image uploader, category and condition pickers. */
export function ItemFormPage() {
  const { id } = useParams();
  const editing = !!id;
  const navigate = useNavigate();
  const toast = useToast();
  const { user } = useAuth();
  const { data: categories } = useCategories();
  const existing = useItem(id ?? '');
  const save = useSaveItem(id);
  const [form, setForm] = useState(empty);
  const [files, setFiles] = useState([]);
  const [removed, setRemoved] = useState([]);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);

  useEffect(() => {
    const it = existing.data;
    if (editing && it) {
      setForm({
        name: it.name,
        categoryId: it.category.id,
        description: it.description,
        condition: it.condition,
        maxLoanDays: String(it.maxLoanDays),
        pickupLocation: it.pickupLocation,
        depositNote: it.depositNote ?? '',
      });
    }
  }, [editing, existing.data]);

  if (editing && existing.isLoading) return <PageSpinner />;
  if (editing && (existing.isError || !existing.data)) {
    return (
      <PageContainer>
        <ErrorState error={existing.error} onRetry={() => existing.refetch()} />
      </PageContainer>
    );
  }
  if (editing && existing.data && existing.data.owner.id !== user?.id) {
    return (
      <PageContainer>
        <Alert tone="error">Only the owner can edit this item.</Alert>
      </PageContainer>
    );
  }

  const set = (k) => (e) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    const errs = validateItem(form);
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }
    setErrors({});
    const fd = new FormData();
    Object.entries(form).forEach(([k, v]) => fd.append(k, v));
    files.forEach((f) => fd.append('images', f));
    if (removed.length) fd.append('removeImageIds', JSON.stringify(removed));
    try {
      const item = await save.mutateAsync(fd);
      toast.success(editing ? 'Changes saved' : 'Your item is listed!');
      navigate(`/items/${item.id}`);
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    }
  };

  return (
    <PageContainer>
      <PageHeader title={editing ? 'Edit item' : 'List an item'} subtitle="Clear photos and an honest condition help borrowers decide." />
      {!user?.emailVerified && <Alert tone="warning" className="mb-4">Verify your email before listing items.</Alert>}
      <form onSubmit={submit} noValidate className="space-y-6">
        <Card className="space-y-4">
          <Input label="Item name" required maxLength={150} value={form.name} onChange={set('name')} error={errors.name} placeholder="e.g. TI-84 Plus graphing calculator" />
          <div className="grid gap-4 sm:grid-cols-2">
            <Select label="Category" required value={form.categoryId} onChange={set('categoryId')} error={errors.categoryId}>
              <option value="" disabled>
                Choose a category
              </option>
              {categories?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
            <Select label="Condition" required value={form.condition} onChange={set('condition')} error={errors.condition}>
              {ItemCondition.map((c) => (
                <option key={c} value={c}>
                  {CONDITION_LABELS[c]}
                </option>
              ))}
            </Select>
          </div>
          <Textarea label="Description" required rows={5} maxLength={5000} value={form.description} onChange={set('description')} error={errors.description} hint="What’s included, any quirks, how to use it." />
        </Card>
        <Card className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Maximum loan (days)" type="number" min={1} max={365} required value={form.maxLoanDays} onChange={set('maxLoanDays')} error={errors.maxLoanDays} />
            <Input label="Pickup location" required maxLength={200} value={form.pickupLocation} onChange={set('pickupLocation')} error={errors.pickupLocation} placeholder="e.g. Library entrance" />
          </div>
          <Input label="Deposit or care note (optional)" maxLength={500} value={form.depositNote} onChange={set('depositNote')} error={errors.depositNote} placeholder="e.g. Please return with fresh batteries" />
        </Card>
        <Card>
          <ImageUploader
            existing={existing.data?.images ?? []}
            removedIds={removed}
            onToggleRemove={(imgId) => setRemoved((r) => (r.includes(imgId) ? r.filter((x) => x !== imgId) : [...r, imgId]))}
            files={files}
            onFilesChange={setFiles}
            onError={(m) => toast.error(m)}
          />
        </Card>
        {error && <Alert tone="error">{error}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => navigate(-1)}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending} disabled={!user?.emailVerified}>
            {editing ? 'Save changes' : 'Publish item'}
          </Button>
        </div>
      </form>
    </PageContainer>
  );
}
