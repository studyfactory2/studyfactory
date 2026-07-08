type StaffPageIconType =
  | 'drink'
  | 'seat'
  | 'cup'
  | 'clipboard'
  | 'staff-leave'
  | 'side-dish';

type StaffPageItem = {
  icon: StaffPageIconType;
  label: string;
  href?: string;
};

type StaffPageSection = {
  title: string;
  items: StaffPageItem[];
};

const STAFF_PAGE_SECTIONS: StaffPageSection[] = [
  {
    title: '업무 관련 메뉴',
    items: [
      { icon: 'drink', label: '음료\n제조', href: '/managerdashboard?view=beverage_making_sheet' },
      { icon: 'drink', label: '음료\n서빙', href: '/managerdashboard?view=beverage_serving_sheet' },
    ],
  },
  {
    title: '스탭 개인 메뉴',
    items: [
      { icon: 'staff-leave', label: '스탭\n휴무 신청', href: '/managerdashboard?view=staff_leave_request' },
      { icon: 'side-dish', label: '반찬\n신청', href: '/managerdashboard?view=staff_side_dish_request' },
    ],
  },
];

export function StaffPagePanel() {
  return (
    <div className="staff-page-panel">
      {STAFF_PAGE_SECTIONS.map((section) => (
        <section className="staff-page-section" key={section.title}>
          <h3>{section.title}</h3>
          <div className="staff-page-buttons">
            {section.items.map((item) => (
              <StaffPageButton item={item} key={item.label} />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function StaffPageButton({ item }: { item: StaffPageItem }) {
  const content = (
    <>
      <StaffPageIcon icon={item.icon} />
      <span>
        {item.label.split('\n').map((line) => (
          <span key={line}>{line}</span>
        ))}
      </span>
    </>
  );

  if (item.href) {
    return (
      <a className="staff-page-button" href={item.href} aria-label={item.label.replace('\n', ' ')}>
        {content}
      </a>
    );
  }

  return (
    <button className="staff-page-button" type="button" aria-label={item.label.replace('\n', ' ')}>
      {content}
    </button>
  );
}

function StaffPageIcon({ icon }: { icon: StaffPageIconType }) {
  if (icon === 'drink') {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <path d="M9 10h14l-1.2 14.3a3 3 0 0 1-3 2.7h-5.6a3 3 0 0 1-3-2.7L9 10Z" />
        <path d="M12 10 13 5" />
        <path d="M20 10V5" />
        <path d="M9.5 15h13" />
        <path d="M23 13h2.5a3.5 3.5 0 0 1 0 7H23" />
      </svg>
    );
  }

  if (icon === 'seat') {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <path d="M16 15a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Z" />
        <path d="M8 26c.8-4.5 3.6-7 8-7s7.2 2.5 8 7" />
        <path d="M16 28s6-5.2 6-10a6 6 0 0 0-12 0c0 4.8 6 10 6 10Z" />
      </svg>
    );
  }

  if (icon === 'cup') {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <path d="M8 10h14v10a6 6 0 0 1-6 6h-2a6 6 0 0 1-6-6V10Z" />
        <path d="M22 13h2a3.5 3.5 0 0 1 0 7h-2" />
        <path d="M11 6v2" />
        <path d="M16 5v3" />
        <path d="M21 6v2" />
      </svg>
    );
  }

  if (icon === 'clipboard') {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <path d="M12 6h8l1 3h3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V11a2 2 0 0 1 2-2h3l1-3Z" />
        <path d="M12 9h8" />
        <path d="M11 16h10" />
        <path d="M11 21h7" />
      </svg>
    );
  }

  if (icon === 'staff-leave') {
    return (
      <svg viewBox="0 0 32 32" aria-hidden="true">
        <path d="M13 15a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Z" />
        <path d="M5 26c.8-4.6 3.7-7 8-7 1.8 0 3.4.5 4.7 1.4" />
        <path d="M23 15v10" />
        <path d="M18 20h10" />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 32 32" aria-hidden="true">
      <path d="M10 8h12l-1.5 18h-9L10 8Z" />
      <path d="M8 8h16" />
      <path d="M13 5h6" />
      <path d="M13 15h6" />
      <path d="M13 20h5" />
    </svg>
  );
}
