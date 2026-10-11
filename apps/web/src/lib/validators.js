/**
 * Small client-side validators (the server validates everything again). Each returns an error
 * message string, or null when the value is valid. The validateX helpers return an object of
 * field -> message for a whole form (empty object = valid).
 */
import { todayStr } from './dates';

/** Campus domains accepted at registration (the API enforces ALLOWED_EMAIL_DOMAIN). */
export const ALLOWED_EMAIL_DOMAINS = (import.meta.env.VITE_ALLOWED_EMAIL_DOMAIN ?? 'campus.edu')
  .split(',')
  .map((d) => d.trim().toLowerCase())
  .filter(Boolean);

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_RE = /^\+?[0-9 ()-]{6,20}$/;

export function required(value, message = 'This field is required') {
  return value === undefined || value === null || String(value).trim() === '' ? message : null;
}

export function length(value, min, max, label = 'This field') {
  const v = String(value ?? '').trim();
  if (v.length < min) return min <= 1 ? `${label} is required` : `${label} must be at least ${min} characters`;
  if (v.length > max) return `${label} must be at most ${max} characters`;
  return null;
}

export function email(value) {
  const v = String(value ?? '').trim();
  if (!v) return 'Email is required';
  if (!EMAIL_RE.test(v) || v.length > 150) return 'Enter a valid email address';
  return null;
}

/** Valid email on one of the campus domains (subdomains allowed). */
export function campusEmail(value, domains = ALLOWED_EMAIL_DOMAINS) {
  const err = email(value);
  if (err) return err;
  const domain = String(value).trim().toLowerCase().split('@')[1];
  if (domains.length && !domains.some((d) => domain === d || domain.endsWith(`.${d}`))) {
    return `Use your campus email (${domains.map((d) => '@' + d).join(', ')})`;
  }
  return null;
}

/** 8–128 characters with at least one letter and one number (same rule as the API). */
export function password(value) {
  const v = String(value ?? '');
  if (v.length < 8) return 'Password must be at least 8 characters';
  if (v.length > 128) return 'Password must be at most 128 characters';
  if (!/[A-Za-z]/.test(v)) return 'Password must contain a letter';
  if (!/\d/.test(v)) return 'Password must contain a number';
  return null;
}

export function phone(value) {
  const v = String(value ?? '').trim();
  return v && !PHONE_RE.test(v) ? 'Invalid phone number' : null;
}

/** YYYY-MM-DD range: both set, end on/after start, and (optionally) start not in the past. */
export function dateRange(start, end, { allowPast = false } = {}) {
  if (!start || !end) return 'Choose a start and an end date';
  if (!allowPast && start < todayStr()) return 'Start date can’t be in the past';
  if (end < start) return 'End date must be on or after the start date';
  return null;
}

export function rating(value) {
  const n = Number(value);
  return Number.isInteger(n) && n >= 1 && n <= 5 ? null : 'Choose a rating from 1 to 5';
}

function collect(checks) {
  return Object.fromEntries(Object.entries(checks).filter(([, msg]) => msg));
}

export function validateRegister(form) {
  return collect({
    name: length(form.name, 2, 100, 'Name'),
    email: campusEmail(form.email),
    password: password(form.password),
    department: length(form.department, 1, 100, 'Department'),
    phone: phone(form.phone),
  });
}

export function validateItem(form) {
  const days = Number(form.maxLoanDays);
  return collect({
    name: length(form.name, 2, 150, 'Name'),
    categoryId: required(form.categoryId, 'Choose a category'),
    description: length(form.description, 1, 5000, 'Description'),
    condition: required(form.condition, 'Choose a condition'),
    maxLoanDays: form.maxLoanDays === '' || (Number.isInteger(days) && days >= 1 && days <= 365) ? null : 'Enter 1 to 365 days',
    pickupLocation: length(form.pickupLocation, 2, 200, 'Pickup location'),
    depositNote: String(form.depositNote ?? '').length > 500 ? 'Must be at most 500 characters' : null,
  });
}
