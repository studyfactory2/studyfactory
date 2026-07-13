import type { ReactNode } from 'react';

type LoginLayoutProps = {
  children: ReactNode;
};

export function LoginLayout({ children }: LoginLayoutProps) {
  return (
    <main className="login-page">
      <section className="brand-panel" aria-label="로그인 브랜드 이미지">
        <video className="brand-character" autoPlay loop muted playsInline preload="auto" aria-label="로그인 캐릭터">
          <source src="/studyfactory-character-walk.mp4" type="video/mp4" />
        </video>
        <h1 className="brand-heading">자격증공장</h1>
      </section>
      <section className="login-card">{children}</section>
    </main>
  );
}
