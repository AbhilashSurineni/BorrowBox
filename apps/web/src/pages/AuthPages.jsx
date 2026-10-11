import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, ButtonLink, Input, PageSpinner } from '@/components/ui';
import { useAuth } from '@/features/auth/AuthProvider';
import { authApi } from '@/features/auth/api';
import { errorMessage, fieldErrors } from '@/lib/errorMessages';
import { validateRegister } from '@/lib/validators';

function AuthCard({ title, subtitle, children, footer }) {
  return (
    <div className="mx-auto max-w-md px-4 py-10 sm:py-16">
      <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm sm:p-8">
        <h1 className="text-2xl font-bold text-gray-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-gray-600">{subtitle}</p>}
        <div className="mt-6">{children}</div>
      </div>
      {footer && <p className="mt-6 text-center text-sm text-gray-700">{footer}</p>}
    </div>
  );
}

const linkCls = 'font-semibold text-brand-700 hover:underline';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate((location.state )?.from ?? '/browse', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <AuthCard title="Welcome back" subtitle="Log in with your campus email." footer={<>New here? <Link to="/register" className={linkCls}>Create an account</Link></>}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Input label="Campus email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input label="Password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        <div className="text-right text-sm">
          <Link to="/forgot-password" className={linkCls}>
            Forgot password?
          </Link>
        </div>
        <Button type="submit" className="w-full" loading={busy}>
          Log in
        </Button>
      </form>
    </AuthCard>
  );
}

export function RegisterPage() {
  const [form, setForm] = useState({ name: '', email: '', password: '', department: '', phone: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const [done, setDone] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    const errs = validateRegister(form);
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }
    setErrors({});
    setBusy(true);
    try {
      await authApi.register({
        name: form.name.trim(),
        email: form.email.trim().toLowerCase(),
        password: form.password,
        department: form.department.trim(),
        phone: form.phone.trim() || undefined,
      });
      setDone(form.email);
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <AuthCard title="Check your inbox" subtitle={<>We sent a verification link to <strong>{done}</strong>.</>}>
        <p className="text-sm text-gray-700">Click the link to activate borrowing and lending. You can log in now and browse in the meantime.</p>
        <ButtonLink to="/login" className="mt-6 w-full">
          Go to log in
        </ButtonLink>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Create your account" subtitle="Only campus email addresses can join." footer={<>Already have an account? <Link to="/login" className={linkCls}>Log in</Link></>}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Input label="Full name" autoComplete="name" required value={form.name} onChange={set('name')} error={errors.name} />
        <Input label="Campus email" type="email" autoComplete="email" required value={form.email} onChange={set('email')} error={errors.email} />
        <Input label="Password" type="password" autoComplete="new-password" required value={form.password} onChange={set('password')} error={errors.password} hint="At least 8 characters, with a letter and a number." />
        <Input label="Department" required value={form.department} onChange={set('department')} error={errors.department} />
        <Input label="Phone (optional)" type="tel" autoComplete="tel" value={form.phone} onChange={set('phone')} error={errors.phone} hint="Only shown to people whose loans you approve or who approve yours." />
        <Button type="submit" className="w-full" loading={busy}>
          Create account
        </Button>
      </form>
    </AuthCard>
  );
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await authApi.forgotPassword(email);
      setSent(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <AuthCard title="Reset your password" subtitle="We’ll email you a link to set a new password." footer={<Link to="/login" className={linkCls}>Back to log in</Link>}>
      {sent ? (
        <Alert tone="success">If an account exists for {email}, a reset link is on its way. It expires in 60 minutes.</Alert>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          {error && <Alert tone="error">{error}</Alert>}
          <Input label="Campus email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <Button type="submit" className="w-full" loading={busy}>
            Send reset link
          </Button>
        </form>
      )}
    </AuthCard>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    if (password !== confirm) return setError('The passwords don’t match.');
    setBusy(true);
    setError(null);
    try {
      await authApi.resetPassword(token, password);
      navigate('/login', { replace: true, state: { reset: true } });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };
  if (!token) {
    return (
      <AuthCard title="Invalid link">
        <Alert tone="error">This reset link is incomplete. Request a new one.</Alert>
        <ButtonLink to="/forgot-password" className="mt-4 w-full">
          Request a new link
        </ButtonLink>
      </AuthCard>
    );
  }
  return (
    <AuthCard title="Choose a new password">
      <form onSubmit={submit} className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        <Input label="New password" type="password" autoComplete="new-password" required value={password} onChange={(e) => setPassword(e.target.value)} hint="At least 8 characters, with a letter and a number." />
        <Input label="Confirm password" type="password" autoComplete="new-password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        <Button type="submit" className="w-full" loading={busy}>
          Update password
        </Button>
      </form>
    </AuthCard>
  );
}

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const { status, user, setUser } = useAuth();
  const [state, setState] = useState('loading');
  const [error, setError] = useState('');
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return; // StrictMode double-invoke: tokens are single use
    started.current = true;
    const token = params.get('token');
    if (!token) {
      setState('error');
      setError('This verification link is incomplete.');
      return;
    }
    authApi.verifyEmail(token)
      .then(() => setState('ok'))
      .catch((err) => {
        setState('error');
        setError(errorMessage(err));
      });
  }, [params]);

  useEffect(() => {
    if (state === 'ok' && user && !user.emailVerified) setUser({ ...user, emailVerified: true });
  }, [state, user, setUser]);

  if (state === 'loading') return <PageSpinner />;
  return (
    <AuthCard title={state === 'ok' ? 'Email verified 🎉' : 'Verification failed'}>
      {state === 'ok' ? <Alert tone="success">You can now borrow and lend items.</Alert> : <Alert tone="error">{error}</Alert>}
      <ButtonLink to={status === 'authenticated' ? '/browse' : '/login'} className="mt-6 w-full">
        {status === 'authenticated' ? 'Start browsing' : 'Log in'}
      </ButtonLink>
    </AuthCard>
  );
}
