import type { ReactNode } from 'react';

type LoginLayoutProps = {
  children: ReactNode;
};

export function LoginLayout({ children }: LoginLayoutProps) {
  return (
    <main className="login-page">
      <section className="brand-panel" aria-label="자격증공장">
        <div className="brand-content">
          <span className="brand-badge">STUDY FACTORY</span>
          <img className="brand-character" src="/studyfactory-character-transparent.png" alt="Study Factory 캐릭터" />
          <p className="eyebrow">오늘의 계획을 가장 쉽게</p>
          <h1>함께 일하고,<br />함께 성장해요</h1>
          <p className="brand-description">작업 계획부터 휴무, 식사 신청까지<br />매일 필요한 일을 한곳에서 관리하세요.</p>
        </div>
      </section>
      <section className="login-card">
        <div className="login-content">
          <div className="login-intro">
            <img className="mobile-brand-character" src="/studyfactory-character-transparent.png" alt="Study Factory 캐릭터" />
            <span>반가워요 👋</span>
            <h2>로그인하고 시작하세요</h2>
            <p>등록한 이름과 4자리 비밀번호를 입력해 주세요.</p>
          </div>
          {children}
        </div>
      </section>
    </main>
  );
}
