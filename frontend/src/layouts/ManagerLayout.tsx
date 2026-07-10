import type { ReactNode } from 'react';

type ManagerLayoutProps = {
  children: ReactNode;
  className?: string;
};

export function ManagerLayout({ children, className }: ManagerLayoutProps) {
  return <main className={className ? `manager-page ${className}` : 'manager-page'}>{children}</main>;
}
