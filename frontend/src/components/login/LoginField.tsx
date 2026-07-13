import type { ReactNode } from 'react';

type LoginFieldProps = {
  children: ReactNode;
  icon?: 'user' | 'lock';
  label: string;
  trailingButton?: ReactNode;
};

export function LoginField({ children, icon, label, trailingButton }: LoginFieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <div className={`login-input-shell${icon ? '' : ' without-icon'}`}>
        {icon && <LoginFieldIcon type={icon} />}
        {children}
        {trailingButton}
      </div>
    </label>
  );
}

function LoginFieldIcon({ type }: { type: 'user' | 'lock' }) {
  if (type === 'lock') {
    return (
      <span className="login-field-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" focusable="false">
          <rect x="5" y="10" width="14" height="10" rx="2.5" />
          <path d="M8 10V8a4 4 0 0 1 8 0v2" />
          <path d="M12 14v2.5" />
        </svg>
      </span>
    );
  }

  return (
    <span className="login-field-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" focusable="false">
        <circle cx="12" cy="8" r="3.5" />
        <path d="M5.5 19a6.5 6.5 0 0 1 13 0" />
      </svg>
    </span>
  );
}

export function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" focusable="false">
      <path d="M2.5 12s3.5-5.5 9.5-5.5 9.5 5.5 9.5 5.5-3.5 5.5-9.5 5.5S2.5 12 2.5 12Z" />
      <circle cx="12" cy="12" r="2.5" />
    </svg>
  );
}

export function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" focusable="false">
      <path d="M2.5 12s3.5-5.5 9.5-5.5 9.5 5.5 9.5 5.5a17 17 0 0 1-2.4 2.7" />
      <path d="M15.1 16.9a9.5 9.5 0 0 1-3.1.6C6 17.5 2.5 12 2.5 12a17.8 17.8 0 0 1 3.4-3.5" />
      <path d="M9.9 9.9a2.5 2.5 0 0 1 3.2 3.2" />
      <path d="M3.5 3.5 20.5 20.5" />
    </svg>
  );
}
