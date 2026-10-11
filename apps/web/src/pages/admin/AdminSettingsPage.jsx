import { useEffect, useState } from 'react';
import { DEFAULT_SETTINGS } from '@/lib/constants';
import { DataTable } from '@/components/DataTable';
import { Alert, Badge, Button, Card, Input, PageHeader, Select, Skeleton, Textarea, useConfirm, useToast } from '@/components/ui';
import { useAdminCategories, useAdminMutation, useAnnouncements, useSettings } from '@/features/admin/hooks';
import { adminApi } from '@/features/admin/api';
import { formatDateTime } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';

const FIELDS = [
  { key: 'max_loan_days_default', label: 'Default max loan (days)', hint: 'Pre-filled for new items.', min: 1, max: 365 },
  { key: 'request_expiry_hours', label: 'Request expiry (hours)', hint: 'Pending requests expire after this.', min: 1, max: 720 },
  { key: 'max_active_loans', label: 'Max active loans per user', hint: 'Approved, active and overdue.', min: 1, max: 50 },
  { key: 'max_pending_requests', label: 'Max pending requests per user', hint: '', min: 1, max: 50 },
  { key: 'reminder_hour', label: 'Daily reminder hour (0–23)', hint: 'Due-date reminders and start-date nudges.', min: 0, max: 23 },
  { key: 'auto_complete_days', label: 'Auto-complete after (days)', hint: 'Returned loans become completed.', min: 1, max: 90 },
  { key: 'auto_suspend_report_threshold', label: 'Auto-suspend after upheld reports', hint: '', min: 1, max: 50 },
];

function SettingsForm() {
  const toast = useToast();
  const { data, isLoading } = useSettings();
  const [form, setForm] = useState({});
  useEffect(() => {
    if (data) setForm(Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])));
  }, [data]);
  const save = useAdminMutation(adminApi.updateSettings);

  const submit = async (e) => {
    e.preventDefault();
    const patch = Object.fromEntries(FIELDS.map((f) => [f.key, Number(form[f.key])])) ;
    try {
      await save.mutateAsync(patch);
      toast.success('Settings saved');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Card>
      <h2 className="text-lg font-semibold text-gray-900">Limits and timing</h2>
      <p className="mb-4 text-sm text-gray-600">Changes apply immediately and are recorded in the audit log.</p>
      {isLoading ? (
        <Skeleton className="h-40" />
      ) : (
        <form onSubmit={submit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            {FIELDS.map((f) => (
              <Input
                key={f.key}
                label={f.label}
                hint={`${f.hint} Default ${DEFAULT_SETTINGS[f.key]}.`.trim()}
                type="number"
                min={f.min}
                max={f.max}
                required
                value={form[f.key] ?? ''}
                onChange={(e) => setForm((s) => ({ ...s, [f.key]: e.target.value }))}
              />
            ))}
          </div>
          <div className="flex justify-end">
            <Button type="submit" loading={save.isPending}>
              Save settings
            </Button>
          </div>
        </form>
      )}
    </Card>
  );
}

function Categories() {
  const toast = useToast();
  const confirm = useConfirm();
  const { data, isLoading, error, refetch } = useAdminCategories();
  const [name, setName] = useState('');
  const [icon, setIcon] = useState('box');
  const create = useAdminMutation(adminApi.createCategory);
  const patch = useAdminMutation((v) => adminApi.updateCategory(v.id, v.body));
  const del = useAdminMutation(adminApi.deleteCategory);

  const wrap = async (p, ok) => {
    try {
      await p;
      toast.success(ok);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Card>
      <h2 className="text-lg font-semibold text-gray-900">Categories</h2>
      <form
        className="my-4 grid gap-3 sm:grid-cols-[1fr_160px_auto] sm:items-end"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!name.trim()) return;
          await wrap(create.mutateAsync({ name: name.trim(), icon }), 'Category added');
          setName('');
        }}
      >
        <Input label="New category" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} required />
        <Select label="Icon" value={icon} onChange={(e) => setIcon(e.target.value)}>
          {['book', 'calculator', 'flask', 'ball', 'cpu', 'pencil', 'wrench', 'box'].map((i) => (
            <option key={i} value={i}>
              {i}
            </option>
          ))}
        </Select>
        <Button type="submit" loading={create.isPending}>
          Add
        </Button>
      </form>
      <DataTable
        caption="Categories"
        rows={data}
        loading={isLoading}
        error={error}
        onRetry={() => refetch()}
        rowKey={(c) => c.id}
        columns={[
          { header: 'Name', cell: (c) => <span className="font-medium">{c.name}</span> },
          { header: 'Items', cell: (c) => c.itemCount },
          { header: 'Status', cell: (c) => (c.isActive ? <Badge tone="green">active</Badge> : <Badge>inactive</Badge>) },
          {
            header: 'Actions',
            cell: (c) => (
              <div className="flex flex-wrap gap-1">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={async () => {
                    const r = await confirm({ title: `Rename “${c.name}”`, input: { label: 'New name', required: true }, confirmLabel: 'Rename' });
                    if (r.confirmed) await wrap(patch.mutateAsync({ id: c.id, body: { name: r.value } }), 'Category renamed');
                  }}
                >
                  Rename
                </Button>
                <Button size="sm" variant="secondary" onClick={() => wrap(patch.mutateAsync({ id: c.id, body: { isActive: !c.isActive } }), c.isActive ? 'Category hidden' : 'Category active')}>
                  {c.isActive ? 'Deactivate' : 'Activate'}
                </Button>
                <Button
                  size="sm"
                  variant="danger"
                  disabled={c.itemCount > 0}
                  title={c.itemCount > 0 ? 'Categories with items can only be deactivated' : undefined}
                  onClick={async () => {
                    const r = await confirm({ title: `Delete “${c.name}”?`, message: 'This can’t be undone.', danger: true, confirmLabel: 'Delete' });
                    if (r.confirmed) await wrap(del.mutateAsync(c.id), 'Category deleted');
                  }}
                >
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />
    </Card>
  );
}

function Announcements() {
  const toast = useToast();
  const confirm = useConfirm();
  const { data } = useAnnouncements({ page: 1, limit: 5 });
  const [form, setForm] = useState({ title: '', body: '', link: '', audience: 'ALL' , sendEmail: false });
  const send = useAdminMutation((v) => adminApi.announce({ ...v, link: v.link || undefined }));

  const submit = async (e) => {
    e.preventDefault();
    const r = await confirm({
      title: 'Send announcement?',
      message: `Every active ${form.audience === 'ALL' ? 'user' : form.audience === 'ADMINS' ? 'admin' : 'member'} gets an in-app notification${form.sendEmail ? ' and an email' : ''}.`,
      confirmLabel: 'Send',
    });
    if (!r.confirmed) return;
    try {
      await send.mutateAsync(form);
      toast.success('Announcement sent');
      setForm({ title: '', body: '', link: '', audience: 'ALL', sendEmail: false });
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Card>
      <h2 className="text-lg font-semibold text-gray-900">Announcements</h2>
      <form onSubmit={submit} className="mt-4 space-y-4">
        <Input label="Title" required maxLength={150} value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />
        <Textarea label="Message" required rows={4} maxLength={5000} value={form.body} onChange={(e) => setForm((f) => ({ ...f, body: e.target.value }))} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Link (optional)" placeholder="/browse" value={form.link} onChange={(e) => setForm((f) => ({ ...f, link: e.target.value }))} />
          <Select label="Audience" value={form.audience} onChange={(e) => setForm((f) => ({ ...f, audience: e.target.value  }))}>
            <option value="ALL">Everyone</option>
            <option value="MEMBERS">Members</option>
            <option value="ADMINS">Admins</option>
          </Select>
        </div>
        <label className="flex items-center gap-3 text-sm text-gray-800">
          <input type="checkbox" className="h-5 w-5 rounded border-gray-400" checked={form.sendEmail} onChange={(e) => setForm((f) => ({ ...f, sendEmail: e.target.checked }))} />
          Also send by email
        </label>
        <div className="flex justify-end">
          <Button type="submit" loading={send.isPending} disabled={!form.title.trim() || !form.body.trim()}>
            Send announcement
          </Button>
        </div>
      </form>
      {!!data?.data.length && (
        <div className="mt-6 border-t border-gray-100 pt-4">
          <h3 className="text-sm font-semibold text-gray-900">Recent</h3>
          <ul className="mt-2 space-y-2 text-sm">
            {data.data.map((a) => (
              <li key={a.id}>
                <span className="font-medium text-gray-900">{a.title}</span>
                <span className="text-gray-600">
                  {' '}
                  · {a.recipientCount} recipients · {formatDateTime(a.createdAt)} · {a.author.name}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

/** Screen 14: categories and limits (plus announcements). */
export default function AdminSettingsPage() {
  return (
    <>
      <PageHeader title="Settings" />
      <div className="space-y-6">
        <Alert tone="info">Every change on this page is written to the audit log.</Alert>
        <SettingsForm />
        <Categories />
        <Announcements />
      </div>
    </>
  );
}
