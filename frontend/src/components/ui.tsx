import { forwardRef } from 'react';
import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
} from 'react';
import { Link } from 'react-router-dom';
import type { LinkProps } from 'react-router-dom';
import { WarningCircle } from '@phosphor-icons/react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

const base =
  'inline-flex items-center justify-center gap-2 min-h-12 px-6 rounded-xl text-body font-semibold ' +
  'transition-[background-color,transform,color] duration-300 ease-soft active:scale-[0.98] ' +
  'disabled:opacity-60 disabled:cursor-not-allowed disabled:active:scale-100 no-underline';

const variants: Record<Variant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover',
  secondary: 'bg-card text-primary border-2 border-primary hover:bg-tint',
  ghost: 'bg-transparent text-body hover:bg-sand',
  danger: 'bg-danger text-white hover:bg-red-800',
};

export const cx = (...parts: (string | false | null | undefined)[]) =>
  parts.filter(Boolean).join(' ');

export function Button({
  variant = 'primary',
  className,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return <button type="button" className={cx(base, variants[variant], className)} {...rest} />;
}

export function LinkButton({
  variant = 'primary',
  className,
  ...rest
}: LinkProps & { variant?: Variant }) {
  return <Link className={cx(base, variants[variant], className)} {...rest} />;
}

const fieldBox =
  'min-h-12 w-full box-border px-3 border border-field rounded-xl bg-card text-body text-ink ' +
  'transition-colors hover:border-busy aria-[invalid=true]:border-danger';

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
  className?: string;
}

export function Field({ label, error, hint, children, className }: FieldProps) {
  return (
    <label className={cx('flex flex-col gap-2 text-small font-semibold text-body', className)}>
      {label}
      {children}
      {hint && !error && <span className="text-caption font-normal text-muted">{hint}</span>}
      {error && (
        <span role="alert" className="flex items-center gap-2 text-small font-normal text-danger">
          <WarningCircle size={16} aria-hidden="true" />
          {error}
        </span>
      )}
    </label>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, ...rest }, ref) {
    return <input ref={ref} className={cx(fieldBox, 'font-normal', className)} {...rest} />;
  },
);

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className, ...rest }, ref) {
    return <select ref={ref} className={cx(fieldBox, 'font-normal', className)} {...rest} />;
  },
);

export function Chip({
  pressed,
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { pressed: boolean }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      className={cx(
        'min-h-11 px-4 rounded-full text-small transition-colors duration-300 ease-soft active:scale-[0.98]',
        pressed
          ? 'border-2 border-primary bg-tint text-primary-hover font-semibold'
          : 'border border-field bg-card text-body hover:bg-sand',
      )}
      {...rest}
    >
      {children}
    </button>
  );
}

export function Tag({
  children,
  tone = 'sand',
}: {
  children: ReactNode;
  tone?: 'sand' | 'tint' | 'danger' | 'busy';
}) {
  const tones = {
    sand: 'bg-sand text-body',
    tint: 'bg-tint text-primary-hover font-semibold',
    danger: 'bg-danger-tint text-danger font-semibold',
    busy: 'bg-track text-body font-semibold',
  };
  return (
    <span
      className={cx(
        'inline-flex items-center px-2 py-1 rounded-full text-caption font-medium',
        tones[tone],
      )}
    >
      {children}
    </span>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cx('skeleton', className)} />;
}

export function InlineError({
  title,
  children,
  action,
}: {
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div
      role="alert"
      className="flex flex-col gap-2 p-4 rounded-xl border border-danger/30 bg-danger-tint text-small text-ink"
    >
      <div className="flex items-center gap-2 font-semibold text-danger">
        <WarningCircle size={20} aria-hidden="true" />
        {title}
      </div>
      {children && <div className="text-body">{children}</div>}
      {action}
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  children,
  action,
}: {
  icon: ReactNode;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-3 p-12 text-center bg-card border border-dashed border-field rounded-2xl">
      <div className="flex items-center justify-center size-12 rounded-xl bg-tint text-primary">
        {icon}
      </div>
      <h3 className="m-0 text-title font-semibold">{title}</h3>
      {children && <p className="m-0 max-w-md text-body text-muted">{children}</p>}
      {action}
    </div>
  );
}

export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="flex flex-col gap-2">
        <h1 className="m-0 text-h3 font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="m-0 text-body text-muted">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
