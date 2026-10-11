export { clsx as cn } from 'clsx';

export function pluralize(n, word, plural = `${word}s`) {
  return `${n} ${n === 1 ? word : plural}`;
}

export function initials(name) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');
}

export const CONDITION_LABELS = { NEW: 'New', GOOD: 'Good', FAIR: 'Fair', WORN: 'Worn' } ;

export const REPORT_REASON_LABELS = {
  SPAM: 'Spam',
  INAPPROPRIATE: 'Inappropriate content',
  FRAUD: 'Fraud or scam',
  DAMAGED_ITEM: 'Item damaged',
  NOT_RETURNED: 'Item not returned',
  HARASSMENT: 'Harassment',
  OTHER: 'Other',
} ;
