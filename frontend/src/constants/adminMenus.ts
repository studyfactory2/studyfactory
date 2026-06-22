export type AdminMenuId = 'register' | 'attendance' | 'staff-work' | 'staff-page';

export type AdminMenu = {
  id: AdminMenuId;
  label: string;
};

export const ADMIN_MENUS: AdminMenu[] = [
  { id: 'register', label: '관리자 페이지' },
  { id: 'attendance', label: '출석부' },
  { id: 'staff-work', label: '스탭 업무 현황' },
  { id: 'staff-page', label: '스텝페이지' },
];

export function resolveAdminMenuId(value: string | null): AdminMenuId {
  return ADMIN_MENUS.some((menu) => menu.id === value) ? (value as AdminMenuId) : 'register';
}

export function getAdminMenuLabel(menuId: AdminMenuId): string {
  return ADMIN_MENUS.find((menu) => menu.id === menuId)?.label || '관리자 페이지';
}
