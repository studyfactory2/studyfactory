import type { ReactNode } from 'react';

type ManagerLayoutProps = {
  children: ReactNode;
};

export function ManagerLayout({ children }: ManagerLayoutProps) {
  return <main className="manager-page">{children}</main>;
}
