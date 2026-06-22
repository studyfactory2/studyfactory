import React from 'react';
import { useEffect } from 'react';
import { useState } from 'react';
import { apiRequest } from '../api/client.js';

const ADMIN_MENUS = [
  { id: 'register', label: '관리자 페이지' },
  { id: 'attendance', label: '출석부' },
  { id: 'staff-work', label: '스탭 업무 현황' },
  { id: 'staff-page', label: '스텝페이지' },
];

export default function ManagerDashboardPage() {
  const role = localStorage.getItem('memberRole');
  const memberName = localStorage.getItem('memberName') || '사용자';
  const searchParams = new URLSearchParams(window.location.search);
  const currentView = searchParams.get('view') || 'register';
  const [branches, setBranches] = useState([]);
  const [nameplates, setNameplates] = useState([]);

  useEffect(() => {
    if (role !== 'ADMIN') {
      return;
    }

    loadOptions();
  }, [role]);

  const loadOptions = async () => {
    try {
      const [branchResponses, nameplateResponses] = await Promise.all([
        apiRequest('/api/branches'),
        apiRequest('/api/nameplate-contents'),
      ]);
      setBranches(branchResponses);
      setNameplates(nameplateResponses);
    } catch {
      setBranches([]);
      setNameplates([]);
    }
  };

  if (role !== 'ADMIN') {
    return (
      <main className="manager-page member-dashboard-page">
        <section className="manager-card member-dashboard-card">
          <p>Study Factory</p>
          <h1>{memberName}님</h1>
          <span>회원 화면은 별도로 설계될 예정입니다.</span>
        </section>
      </main>
    );
  }

  return (
    <main className="manager-page">
      <ManagerTopBar />
      <ManagerTabs currentView={currentView} />
      <section className="manager-card">
        {currentView === 'register' ? (
          <PreRegistrationPanel branches={branches} nameplates={nameplates} />
        ) : (
          <PlaceholderPanel currentView={currentView} />
        )}
      </section>
      <div className="manager-pagination" aria-hidden="true">
        <span className="active" />
        <span />
        <span />
        <span />
      </div>
    </main>
  );
}

function ManagerTopBar() {
  return (
    <div className="manager-topbar">
      <button className="round-action" type="button" aria-label="나가기">
        <span>↪</span>
      </button>
      <div className="round-logo">
        <img src="/studyfactory-character.png" alt="자격증공장" />
      </div>
      <button className="round-action" type="button" aria-label="새로고침" onClick={() => window.location.reload()}>
        <span>↻</span>
      </button>
    </div>
  );
}

function ManagerTabs({ currentView }) {
  return (
    <nav className="manager-tabs" aria-label="관리자 메뉴">
      <span className="tab-arrow">‹</span>
      {ADMIN_MENUS.map((menu) => (
        <a className={currentView === menu.id ? 'active' : ''} href={`/managerdashboard?view=${menu.id}`} key={menu.id}>
          {menu.label}
        </a>
      ))}
      <span className="tab-arrow">›</span>
    </nav>
  );
}

function PreRegistrationPanel({ branches, nameplates }) {
  const branchOptions = branches.length > 0 ? branches : [{ id: 1, name: '망미점' }];

  return (
    <div className="pre-register-panel">
      <header className="panel-title">
        <button type="button" aria-label="뒤로가기">‹</button>
        <h1>사원 사전 등록</h1>
      </header>
      <form className="pre-register-form">
        <label>
          <span>지점</span>
          <select defaultValue={branchOptions[0]?.id}>
            {branchOptions.map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>사원 구분</span>
          <select defaultValue="MEMBER">
            <option value="MEMBER">회원</option>
            <option value="STAFF">스탭</option>
            <option value="ADMIN">관리자</option>
          </select>
        </label>
        <label className="wide-field">
          <span>이름 (로그인 ID)</span>
          <input type="text" placeholder="이름을 입력하여 주세요." />
        </label>
        <label>
          <span>좌석 번호</span>
          <input type="number" placeholder="번호" />
        </label>
        <label>
          <span>입사예정일</span>
          <input type="date" />
        </label>
        <label>
          <span>명패 내용</span>
          <input list="nameplate-options" type="text" placeholder="명패 문구 입력 또는 선택" />
          <datalist id="nameplate-options">
            {nameplates.map((nameplate) => (
              <option key={nameplate.id} value={nameplate.content} />
            ))}
          </datalist>
        </label>
        <label className="full-field">
          <span>음료 설정 (선택사항)</span>
          <input type="text" placeholder="예: 선식, 텀블러 아아" />
        </label>
        <label className="full-field">
          <span>음료 참고사항</span>
          <textarea placeholder="음료 참고사항" rows={1} />
        </label>
        <label className="full-field">
          <span>회원 참고사항</span>
          <textarea placeholder="참고사항을 입력하세요." rows={1} />
        </label>
        <button className="register-submit" type="button">등록하기</button>
      </form>
      <section className="waiting-panel">
        <h2>등록 대기 현황 (0)</h2>
        <p>대기 중인 인원이 없습니다.</p>
      </section>
    </div>
  );
}

function PlaceholderPanel({ currentView }) {
  const label = ADMIN_MENUS.find((menu) => menu.id === currentView)?.label || '관리자 페이지';

  return (
    <div className="placeholder-panel">
      <strong>{label}</strong>
      <p>화면 연결 영역입니다.</p>
    </div>
  );
}
