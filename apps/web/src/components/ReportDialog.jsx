import { useState } from 'react';
import { ReportReason } from '@/lib/constants';
import { useCreateReport } from '@/features/users/hooks';
import { errorMessage } from '@/lib/errorMessages';
import { REPORT_REASON_LABELS } from '@/lib/utils';
import { Alert, Button, Modal, Select, Textarea, useToast } from './ui';

export function ReportDialog({ targetType, targetId, targetName, open, onClose }) {
  const toast = useToast();
  const report = useCreateReport();
  const [reason, setReason] = useState('OTHER');
  const [description, setDescription] = useState('');
  const [error, setError] = useState(null);

  const submit = async () => {
    setError(null);
    try {
      await report.mutateAsync({ targetType, targetId, reason, description: description.trim() });
      toast.success('Thanks. Our moderators will review your report.');
      setDescription('');
      onClose();
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Report ${targetType === 'ITEM' ? 'item' : 'user'}`}
      description={`Tell the moderators what’s wrong with “${targetName}”.`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="danger" loading={report.isPending} disabled={!description.trim()} onClick={submit}>
            Send report
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Select label="Reason" value={reason} onChange={(e) => setReason(e.target.value )} required>
          {ReportReason.map((r) => (
            <option key={r} value={r}>
              {REPORT_REASON_LABELS[r]}
            </option>
          ))}
        </Select>
        <Textarea label="What happened?" required rows={4} maxLength={2000} value={description} onChange={(e) => setDescription(e.target.value)} />
        {error && <Alert tone="error">{error}</Alert>}
      </div>
    </Modal>
  );
}
