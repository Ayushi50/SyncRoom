import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../lib/api';
import { Button, Field, InlineError, Input } from '../components/ui';

function safeNext(raw: string | null): string {
  return raw && raw.startsWith('/') && !raw.startsWith('//') ? raw : '/rooms/find';
}

function Frame({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
}) {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-6 p-4">
      <Link
        to="/"
        className="text-title font-semibold text-primary no-underline min-h-11 flex items-center"
      >
        Rooms
      </Link>
      <main className="w-full max-w-md box-border bg-card border border-line rounded-2xl p-6 md:p-8 flex flex-col gap-6">
        <div className="flex flex-col gap-2">
          <h1 className="m-0 text-title font-semibold">{title}</h1>
          <p className="m-0 text-small text-muted">{subtitle}</p>
        </div>
        {children}
      </main>
      <p className="m-0 text-small text-muted">{footer}</p>
    </div>
  );
}

const loginSchema = z.object({
  email: z.string().min(1, 'Enter your email').email('Enter a valid email address'),
  password: z.string().min(1, 'Enter your password'),
});

export function Login() {
  const { user, login } = useAuth();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const nav = useNavigate();
  const [error, setError] = useState<ApiError | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema) });
  if (user) return <Navigate to={next} replace />;
  return (
    <Frame
      title="Sign in"
      subtitle="Use your work email to find and book rooms."
      footer={
        <>
          New here?{' '}
          <Link
            to={`/register?next=${encodeURIComponent(next)}`}
            className="text-primary font-semibold"
          >
            Create an account
          </Link>
        </>
      }
    >
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={handleSubmit(async (v) => {
          setError(null);
          try {
            await login(v.email, v.password);
            nav(next, { replace: true });
          } catch (e) {
            setError(e as ApiError);
          }
        })}
      >
        <Field label="Email" error={errors.email?.message}>
          <Input
            type="email"
            autoComplete="email"
            aria-invalid={!!errors.email}
            {...register('email')}
          />
        </Field>
        <Field label="Password" error={errors.password?.message}>
          <Input
            type="password"
            autoComplete="current-password"
            aria-invalid={!!errors.password}
            {...register('password')}
          />
        </Field>
        {error && <InlineError title={error.title}>{error.message}</InlineError>}
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Signing in' : 'Sign in'}
        </Button>
      </form>
    </Frame>
  );
}

const registerSchema = z.object({
  name: z.string().trim().min(1, 'Enter your name').max(120),
  email: z.string().min(1, 'Enter your email').email('Enter a valid email address'),
  password: z.string().min(8, 'Use at least 8 characters').max(72, 'Use 72 characters or fewer'),
});

export function Register() {
  const { user, register: signUp } = useAuth();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const nav = useNavigate();
  const [error, setError] = useState<ApiError | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof registerSchema>>({ resolver: zodResolver(registerSchema) });
  if (user) return <Navigate to={next} replace />;
  return (
    <Frame
      title="Create your account"
      subtitle="It takes a minute. You can book a room right after."
      footer={
        <>
          Already have an account?{' '}
          <Link
            to={`/login?next=${encodeURIComponent(next)}`}
            className="text-primary font-semibold"
          >
            Sign in
          </Link>
        </>
      }
    >
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={handleSubmit(async (v) => {
          setError(null);
          try {
            await signUp(v.name, v.email, v.password);
            nav(next, { replace: true });
          } catch (e) {
            setError(e as ApiError);
          }
        })}
      >
        <Field label="Full name" error={errors.name?.message}>
          <Input autoComplete="name" aria-invalid={!!errors.name} {...register('name')} />
        </Field>
        <Field label="Email" error={errors.email?.message}>
          <Input
            type="email"
            autoComplete="email"
            aria-invalid={!!errors.email}
            {...register('email')}
          />
        </Field>
        <Field label="Password" error={errors.password?.message} hint="At least 8 characters.">
          <Input
            type="password"
            autoComplete="new-password"
            aria-invalid={!!errors.password}
            {...register('password')}
          />
        </Field>
        {error && <InlineError title={error.title}>{error.message}</InlineError>}
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating account' : 'Create account'}
        </Button>
      </form>
    </Frame>
  );
}
