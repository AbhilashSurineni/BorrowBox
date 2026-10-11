import { ButtonLink } from '@/components/ui';

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-md px-4 py-20 text-center">
      <p className="text-5xl" aria-hidden>
        🧭
      </p>
      <h1 className="mt-4 text-2xl font-bold text-gray-900">Page not found</h1>
      <p className="mt-2 text-gray-700">The page you’re looking for doesn’t exist or has moved.</p>
      <ButtonLink to="/" className="mt-6">
        Back to home
      </ButtonLink>
    </div>
  );
}
