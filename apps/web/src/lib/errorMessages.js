import { ApiError } from './api';

/** Friendly copy for every API error code (keep in sync with the API's ErrorCode enum). */
export const ERROR_MESSAGES = {
  VALIDATION_ERROR: 'Some details look wrong. Please check the form and try again.',
  UNAUTHENTICATED: 'Please log in to continue.',
  INVALID_CREDENTIALS: 'That email and password don’t match. Try again or reset your password.',
  INVALID_TOKEN: 'This link is invalid or has expired. Request a new one.',
  FORBIDDEN: 'You don’t have permission to do that.',
  NOT_FOUND: 'We couldn’t find that. It may have been removed.',
  RATE_LIMITED: 'Too many attempts. Please wait a few minutes and try again.',
  INTERNAL: 'Something went wrong on our side. Please try again.',
  EMAIL_TAKEN: 'An account with this email already exists. Try logging in instead.',
  EMAIL_DOMAIN_NOT_ALLOWED: 'Please use your campus email address.',
  EMAIL_NOT_VERIFIED: 'Verify your email address first. Check your inbox for the link.',
  ACCOUNT_SUSPENDED: 'Your account is suspended. Contact the BorrowBox team if you think this is a mistake.',
  ACCOUNT_BANNED: 'Your account has been banned.',
  DATES_UNAVAILABLE: 'Someone has already booked this item for some of those dates. Pick different dates.',
  EXCEEDS_MAX_DURATION: 'That’s longer than the owner allows. Choose a shorter period.',
  OWN_ITEM: 'This is your own item, so you can’t request it.',
  OVERDUE_LOAN_EXISTS: 'You have an overdue item. Return it before making new requests.',
  TOO_MANY_PENDING: 'You already have the maximum number of pending requests. Wait for a reply or cancel one.',
  TOO_MANY_ACTIVE_LOANS: 'You already have the maximum number of active loans. Return something first.',
  INVALID_DATES: 'Those dates don’t work. Start dates can’t be in the past.',
  ITEM_UNAVAILABLE: 'This item isn’t available for requests right now.',
  DUPLICATE_REQUEST: 'You already have a pending request for these dates.',
  INVALID_TRANSITION: 'This loan has changed since you opened it. Refresh and try again.',
  ALREADY_MARKED: 'You’ve already done this step. Waiting for the other person to confirm.',
  EXTENSION_ALREADY_USED: 'An extension has already been requested for this loan.',
  NO_PENDING_EXTENSION: 'There’s no extension request waiting for a decision.',
  HAS_ACTIVE_LOANS: 'Finish or cancel the active loans first.',
  TOO_MANY_IMAGES: 'You can add up to 5 images.',
  INVALID_IMAGE: 'Images must be JPEG, PNG or WebP and 5 MB or smaller.',
  REVIEW_EXISTS: 'You’ve already reviewed this loan.',
  REVIEW_NOT_ALLOWED: 'Reviews open once the item has been returned.',
  DUPLICATE_REPORT: 'You’ve already reported this. Our team is looking into it.',
  CANNOT_REPORT_SELF: 'You can’t report yourself or your own items.',
  CATEGORY_IN_USE: 'This category still has items. Deactivate it instead.',
  CATEGORY_EXISTS: 'A category with this name already exists.',
  CONFLICT: 'That conflicts with the current state. Refresh and try again.',
};

export function errorMessage(err) {
  if (err instanceof ApiError) {
    if (err.code === 'NETWORK_ERROR') return err.message;
    return ERROR_MESSAGES[err.code ] ?? err.message;
  }
  return 'Something went wrong. Please try again.';
}

/** Zod field errors from a 400 VALIDATION_ERROR, keyed by field name. */
export function fieldErrors(err) {
  if (!(err instanceof ApiError) || err.code !== 'VALIDATION_ERROR') return {};
  const details = err.details ;
  return Object.fromEntries(Object.entries(details?.fieldErrors ?? {}).map(([k, v]) => [k, v[0]]));
}
