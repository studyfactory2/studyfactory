import type { ReactNode } from 'react';

type LoginLayoutProps = {
  children: ReactNode;
};

export function LoginLayout({ children }: LoginLayoutProps) {
  return (
    <main className="login-page">
      <section className="brand-panel" aria-label="자격증공장">
        <div className="brand-character-frame" role="img" aria-label="Study Factory 캐릭터 영상">
          <video className="brand-character" autoPlay loop muted playsInline preload="auto" aria-hidden="true">
            <source src="/studyfactory-character-walk.mp4" type="video/mp4" />
          </video>
        </div>
        <p className="brand-title" aria-label="자격증 공장">
          <span aria-hidden="true">자</span>
          <span aria-hidden="true">격</span>
          <span aria-hidden="true">증</span>
          <span aria-hidden="true">공</span>
          <span aria-hidden="true">장</span>
        </p>
      </section>
      <section className="login-card">{children}</section>
    </main>
  );
}
