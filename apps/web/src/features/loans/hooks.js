import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { qk } from '../keys';
import { loansApi } from './api';

/** params: { role?: 'borrower'|'owner', status?: string[], page?, limit? } */
export const useLoans = (params) =>
  useQuery({
    queryKey: qk.loans(params),
    queryFn: () => loansApi.list(params),
    placeholderData: keepPreviousData,
  });

export const useLoan = (id) => useQuery({ queryKey: qk.loan(id), queryFn: () => loansApi.get(id), refetchInterval: 60_000 });

function useInvalidateLoanData() {
  const qc = useQueryClient();
  return () => Promise.all(['loans', 'items', 'my-items', 'notifications'].map((k) => qc.invalidateQueries({ queryKey: [k] })));
}

export function useCreateLoan(itemId) {
  const invalidate = useInvalidateLoanData();
  return useMutation({ mutationFn: (body) => loansApi.request(itemId, body), onSuccess: invalidate });
}

/**
 * One mutation for every loan transition; the server's LoanService enforces the rules.
 * cmd.kind: approve | reject | cancel | handover | return | extension | extension-decision
 */
export function useLoanCommand(loanId) {
  const qc = useQueryClient();
  const invalidate = useInvalidateLoanData();
  return useMutation({
    mutationFn: (cmd) => {
      switch (cmd.kind) {
        case 'approve':
          return loansApi.approve(loanId);
        case 'reject':
          return loansApi.reject(loanId, cmd.reason);
        case 'cancel':
          return loansApi.cancel(loanId, cmd.reason);
        case 'handover':
          return loansApi.handover(loanId);
        case 'return':
          return loansApi.markReturned(loanId, cmd.conditionNote);
        case 'extension':
          return loansApi.requestExtension(loanId, cmd.until, cmd.message);
        case 'extension-decision':
          return loansApi.decideExtension(loanId, cmd.approve);
        default:
          throw new Error(`Unknown loan command: ${cmd.kind}`);
      }
    },
    onSuccess: (loan) => {
      qc.setQueryData(qk.loan(loanId), loan);
      void invalidate();
    },
  });
}

export function useCreateReview(loanId) {
  const invalidate = useInvalidateLoanData();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body) => loansApi.review(loanId, body),
    onSuccess: () => {
      void invalidate();
      void qc.invalidateQueries({ queryKey: ['users'] });
    },
  });
}
