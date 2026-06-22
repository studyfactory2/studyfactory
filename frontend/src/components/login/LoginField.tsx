import type { ReactNode } from 'react';

type LoginFieldProps = {
  children: ReactNode;
  label: string;
};

export function LoginField({ children, label }: LoginFieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}
