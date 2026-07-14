import type { ReactNode } from 'react';

type LoginLayoutProps = {
  children: ReactNode;
};

export function LoginLayout({ children }: LoginLayoutProps) {
  return (
    <main className="login-page">
      <section className="brand-panel" aria-label="자격증공장">
        <img className="brand-character" src="/studyfactory-character.png" alt="Study Factory 캐릭터" />
      </section>
      <section className="login-card">{children}</section>
    </main>
  );
}
