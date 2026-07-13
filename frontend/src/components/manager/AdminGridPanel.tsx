type AdminGridItem = {
  icon: 'member-add' | 'member' | 'calendar' | 'person-check' | 'pin';
  label: string;
  href?: string;
};

type AdminGridSection = {
  title: string;
  items: AdminGridItem[];
};

const ADMIN_GRID_SECTIONS: AdminGridSection[] = [
  {
    title: '사원 관리',
    items: [
      { icon: 'member-add', label: '사원 등록', href: '/managerdashboard?view=register' },
      { icon: 'member', label: '사원 현황', href: '/managerdashboard?view=status' },
    ],
  },
  {
    title: '출석 및 휴무 관리',
    items: [
      { icon: 'calendar', label: '사원별\n휴가 현황', href: '/managerdashboard?view=vacation_history' },
      { icon: 'person-check', label: '사원 기타\n휴무 신청', href: '/managerdashboard?view=other_leave_request' },
      { icon: 'pin', label: '고정 기타\n휴무 관리', href: '/managerdashboard?view=fixed_leave_management' },
    ],
  },
];

export function AdminGridPanel() {
  return (
    <div className="admin-grid-panel">
      {ADMIN_GRID_SECTIONS.map((section) => (
        <section className="admin-grid-section" key={section.title}>
          <h3>{section.title}</h3>
          <div className="admin-grid-buttons">
            {section.items.map((item) => (
              <AdminGridButton item={item} key={item.label} />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function AdminGridButton({ item }: { item: AdminGridItem }) {
  const content = (
    <>
      <AdminGridIcon icon={item.icon} />
      <span>
        {item.label.split('\n').map((line) => (
          <span key={line}>{line}</span>
        ))}
      </span>
    </>
  );

  if (item.href) {
    return (
      <a className="admin-grid-button" href={item.href} aria-label={item.label.replace('\n', ' ')}>
        {content}
      </a>
    );
  }

  return (
    <button className="admin-grid-button" type="button" aria-label={item.label.replace('\n', ' ')}>
      {content}
    </button>
  );
}

function AdminGridIcon({ icon }: { icon: AdminGridItem['icon'] }) {
  if (icon === 'member-add') {
    return (
      <span className="admin-grid-icon admin-grid-icon-member-add" aria-hidden="true">
        <svg viewBox="0 0 32 32">
          <path d="M13.5 14.8a4.8 4.8 0 1 0 0-9.6 4.8 4.8 0 0 0 0 9.6Z" />
          <path d="M5.2 27c.9-5.2 3.9-7.8 8.3-7.8 2.1 0 3.9.6 5.2 1.7" />
          <path d="M24 12.5v9" />
          <path d="M19.5 17h9" />
        </svg>
      </span>
    );
  }

  if (icon === 'member') {
    return (
      <span className="admin-grid-icon admin-grid-icon-member" aria-hidden="true">
        <svg viewBox="0 0 32 32">
          <path d="M13.5 14.8a4.8 4.8 0 1 0 0-9.6 4.8 4.8 0 0 0 0 9.6Z" />
          <path d="M5.2 27c.9-5.2 3.9-7.8 8.3-7.8 2.4 0 4.3.7 5.7 2.2" />
          <path d="m21 11.8 2.5 2.5 5.2-5.8" />
        </svg>
      </span>
    );
  }

  if (icon === 'calendar') {
    return (
      <span className="admin-grid-icon admin-grid-icon-calendar" aria-hidden="true">
        <svg viewBox="0 0 32 32">
          <path d="M9 5.5v5" />
          <path d="M23 5.5v5" />
          <path d="M5.5 12.2h21" />
          <path d="M7.8 8.2h16.4a2.3 2.3 0 0 1 2.3 2.3v15a2.3 2.3 0 0 1-2.3 2.3H7.8a2.3 2.3 0 0 1-2.3-2.3v-15a2.3 2.3 0 0 1 2.3-2.3Z" />
        </svg>
      </span>
    );
  }

  if (icon === 'pin') {
    return (
      <span className="admin-grid-icon admin-grid-icon-pin" aria-hidden="true">
        <svg viewBox="0 0 32 32">
          <path d="M12.2 5.2h7.6" />
          <path d="M14.2 5.2v9.2l-4.4 4.8h12.4l-4.4-4.8V5.2" />
          <path d="M16 19.2v7.6" />
        </svg>
      </span>
    );
  }

  return (
    <span className="admin-grid-icon admin-grid-icon-person-check" aria-hidden="true">
      <svg viewBox="0 0 32 32">
        <path d="M13.5 14.8a4.8 4.8 0 1 0 0-9.6 4.8 4.8 0 0 0 0 9.6Z" />
        <path d="M5.2 27c.9-5.2 3.9-7.8 8.3-7.8 2.1 0 3.9.6 5.2 1.7" />
        <path d="M24 12.5v9" />
        <path d="M19.5 17h9" />
      </svg>
    </span>
  );
}
