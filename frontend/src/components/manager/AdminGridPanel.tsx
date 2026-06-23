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
      { icon: 'person-check', label: '사원 기타\n휴무 신청' },
      { icon: 'pin', label: '고정 기타\n휴무 관리' },
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
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <path d="M14 16a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z" />
        <path d="M5 27c.9-5 4.1-7.6 9-7.6 2.2 0 4 .5 5.4 1.5" />
        <path d="M24 14v8" />
        <path d="M20 18h8" />
      </svg>
    );
  }

  if (icon === 'member') {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <path d="M13 16a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z" />
        <path d="M4.5 27c.9-5 4-7.6 8.5-7.6 3 0 5.4 1.1 7 3.2" />
        <path d="M22 9.5a4 4 0 0 1 0 7.6" />
        <path d="M23 20c2.4.7 4 2.6 4.5 5.7" />
      </svg>
    );
  }

  if (icon === 'calendar') {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <path d="M8 6v5" />
        <path d="M24 6v5" />
        <path d="M5.5 12h21" />
        <path d="M7 8.5h18a2 2 0 0 1 2 2v15a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-15a2 2 0 0 1 2-2Z" />
      </svg>
    );
  }

  if (icon === 'pin') {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <path d="m12 4 8 8" />
        <path d="m9 15 8 8" />
        <path d="m19.5 10.5 2.8 2.8-6.2 6.2-3.6-.4-.4-3.6 6.2-6.2Z" />
        <path d="M14 22 8 28" />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 32 32" aria-hidden="true">
      <path d="M13 16a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z" />
      <path d="M4.5 27c.9-5 4-7.6 8.5-7.6 2 0 3.7.5 5.1 1.5" />
      <path d="m21 17 2.5 2.5L29 14" />
    </svg>
  );
}
