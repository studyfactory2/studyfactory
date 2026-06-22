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
  const logout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('memberName');
    localStorage.removeItem('memberId');
    localStorage.removeItem('branchId');
    localStorage.removeItem('memberRole');
    window.location.href = '/login';
  };

  return (
    <div className="manager-topbar">
      <button className="round-action" type="button" aria-label="로그아웃" onClick={logout}>
        <LogoutIcon />
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

function LogoutIcon() {
  return (
    <svg className="logout-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M10 4H6.5C5.7 4 5 4.7 5 5.5v13C5 19.3 5.7 20 6.5 20H10" />
      <path d="M10 12h8" />
      <path d="m15 8 4 4-4 4" />
    </svg>
  );
}

function ManagerTabs({ currentView }) {
  const currentIndex = Math.max(ADMIN_MENUS.findIndex((menu) => menu.id === currentView), 0);
  const previousMenu = ADMIN_MENUS[currentIndex - 1];
  const currentMenu = ADMIN_MENUS[currentIndex];
  const nextMenu = ADMIN_MENUS[currentIndex + 1];

  return (
    <nav className="manager-tabs" aria-label="관리자 메뉴">
      <div className="adjacent-tab previous-tab">
        {previousMenu && <span>{previousMenu.label}</span>}
      </div>
      {previousMenu ? (
        <a className="tab-arrow previous-arrow" href={`/managerdashboard?view=${previousMenu.id}`} aria-label="이전 페이지">
          ‹
        </a>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      <a className="current-tab" href={`/managerdashboard?view=${currentMenu.id}`} aria-current="page">
        {currentMenu.label}
      </a>
      {nextMenu ? (
        <a className="tab-arrow next-arrow" href={`/managerdashboard?view=${nextMenu.id}`} aria-label="다음 페이지">
          ›
        </a>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      <div className="adjacent-tab next-tab">
        {nextMenu && <span>{nextMenu.label}</span>}
      </div>
    </nav>
  );
}

function PreRegistrationPanel({ branches, nameplates }) {
  const branchOptions = branches.length > 0 ? branches : [{ id: 1, name: '망미점' }];
  const roleOptions = [
    { value: 'MEMBER', label: '회원' },
    { value: 'STAFF', label: '스탭' },
    { value: 'ADMIN', label: '관리자' },
  ];
  const [selectedBranchId, setSelectedBranchId] = useState('');
  const [selectedRole, setSelectedRole] = useState('MEMBER');
  const [branchOpen, setBranchOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);

  useEffect(() => {
    if (selectedBranchId || branchOptions.length === 0) {
      return;
    }

    setSelectedBranchId(String(branchOptions[0].id));
  }, [branchOptions, selectedBranchId]);

  const selectedBranch = branchOptions.find((branch) => String(branch.id) === selectedBranchId) || branchOptions[0];
  const selectedRoleOption = roleOptions.find((role) => role.value === selectedRole) || roleOptions[0];

  return (
    <div className="pre-register-panel">
      <header className="panel-title">
        <button type="button" aria-label="뒤로가기">‹</button>
        <h1>사원 사전 등록</h1>
      </header>
      <form className="pre-register-form">
        <div className="form-field">
          <span>지점</span>
          <FormDropdown
            label="지점"
            open={branchOpen}
            options={branchOptions.map((branch) => ({ value: String(branch.id), label: branch.name }))}
            selectedOption={{ value: String(selectedBranch?.id), label: selectedBranch?.name || '지점을 선택해주세요' }}
            onToggle={() => {
              setBranchOpen((current) => !current);
              setRoleOpen(false);
            }}
            onSelect={(value) => {
              setSelectedBranchId(value);
              setBranchOpen(false);
            }}
          />
        </div>
        <div className="form-field">
          <span>사원 구분</span>
          <FormDropdown
            label="사원 구분"
            open={roleOpen}
            options={roleOptions}
            selectedOption={selectedRoleOption}
            onToggle={() => {
              setRoleOpen((current) => !current);
              setBranchOpen(false);
            }}
            onSelect={(value) => {
              setSelectedRole(value);
              setRoleOpen(false);
            }}
          />
        </div>
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

function FormDropdown({ label, open, options, selectedOption, onToggle, onSelect }) {
  return (
    <div className="form-dropdown">
      <button
        className={`form-dropdown-button${open ? ' open' : ''}`}
        type="button"
        aria-label={selectedOption.label}
        aria-expanded={open}
        aria-haspopup="listbox"
        onClick={onToggle}
      >
        <span>{selectedOption.label}</span>
      </button>
      {open && (
        <div className="form-dropdown-menu" role="listbox" aria-label={label}>
          {options.map((option) => (
            <button
              className={`form-dropdown-option${option.value === selectedOption.value ? ' selected' : ''}`}
              key={option.value}
              type="button"
              role="option"
              aria-selected={option.value === selectedOption.value}
              onClick={() => onSelect(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      )}
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
