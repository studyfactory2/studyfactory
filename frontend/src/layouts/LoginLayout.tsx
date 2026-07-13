import type { ReactNode } from 'react';

type LoginLayoutProps = {
  children: ReactNode;
};

export function LoginLayout({ children }: LoginLayoutProps) {
  return (
    <main className="login-page">
      <section className="brand-panel" aria-label="로그인 브랜드 이미지">
        <div className="brand-character-ring" aria-hidden="true">
          <video className="brand-character" autoPlay loop muted playsInline preload="auto">
            <source src="/studyfactory-character-walk-mint.mp4" type="video/mp4" />
          </video>
        </div>
        <h1 className="brand-heading">자격증공장</h1>
      </section>
      <section className="login-card">{children}</section>
    </main>
  );
}
