import { useEffect, useMemo, useRef } from 'react';

import { Button } from './ui';

const MAX = 5;
const MAX_BYTES = 5 * 1024 * 1024;
const TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/** Image picker with previews; enforces the API limits (5 images, 5 MB, JPEG/PNG/WebP). */
export function ImageUploader({ existing, removedIds, onToggleRemove, files, onFilesChange, onError }) {
  const inputRef = useRef(null);
  const previews = useMemo(() => files.map((f) => URL.createObjectURL(f)), [files]);
  useEffect(() => () => {
    previews.forEach((u) => URL.revokeObjectURL(u));
  }, [previews]);

  const kept = existing.filter((i) => !removedIds.includes(i.id)).length;
  const slots = MAX - kept - files.length;

  const add = (list) => {
    if (!list) return;
    const picked = Array.from(list);
    const bad = picked.find((f) => !TYPES.includes(f.type) || f.size > MAX_BYTES);
    if (bad) onError(`“${bad.name}” must be a JPEG, PNG or WebP image of 5 MB or less.`);
    const ok = picked.filter((f) => TYPES.includes(f.type) && f.size <= MAX_BYTES);
    if (ok.length > slots) onError(`You can add ${slots} more image${slots === 1 ? '' : 's'} (5 max).`);
    onFilesChange([...files, ...ok.slice(0, Math.max(0, slots))]);
    if (inputRef.current) inputRef.current.value = '';
  };

  return (
    <fieldset>
      <legend className="mb-1 text-sm font-medium text-gray-800">Photos</legend>
      <p className="mb-3 text-xs text-gray-600">Up to 5 images, JPEG, PNG or WebP, 5 MB each. Location metadata is removed automatically.</p>
      <ul className="grid grid-cols-3 gap-3 sm:grid-cols-5">
        {existing.map((img) => {
          const removed = removedIds.includes(img.id);
          return (
            <li key={img.id} className="relative">
              <img src={img.url} alt="" className={`aspect-square w-full rounded-lg object-cover ${removed ? 'opacity-30' : ''}`} />
              <button type="button" onClick={() => onToggleRemove(img.id)} className="absolute right-1 top-1 rounded-full bg-white/95 px-2 py-0.5 text-xs font-medium text-gray-900 shadow">
                {removed ? 'Undo' : 'Remove'}
              </button>
            </li>
          );
        })}
        {previews.map((url, i) => (
          <li key={url} className="relative">
            <img src={url} alt={`New image ${i + 1}`} className="aspect-square w-full rounded-lg object-cover ring-2 ring-brand-700" />
            <button
              type="button"
              onClick={() => onFilesChange(files.filter((_, j) => j !== i))}
              className="absolute right-1 top-1 rounded-full bg-white/95 px-2 py-0.5 text-xs font-medium text-gray-900 shadow"
              aria-label={`Remove new image ${i + 1}`}
            >
              Remove
            </button>
          </li>
        ))}
        {slots > 0 && (
          <li>
            <Button variant="secondary" className="aspect-square h-auto w-full flex-col border-dashed text-gray-700" onClick={() => inputRef.current?.click()}>
              <span className="text-2xl" aria-hidden>
                +
              </span>
              <span className="text-xs">Add photo</span>
            </Button>
          </li>
        )}
      </ul>
      <input ref={inputRef} type="file" accept={TYPES.join(',')} multiple className="sr-only" tabIndex={-1} aria-hidden onChange={(e) => add(e.target.files)} />
    </fieldset>
  );
}
