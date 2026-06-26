export type AdminMenuId =
  | 'grid'
  | 'register'
  | 'status'
  | 'vacation_history'
  | 'other_leave_request'
  | 'fixed_leave_management'
  | 'attendance'
  | 'staff-work'
  | 'staff-page'
  | 'daily_leave_status'
  | 'beverage_serving_sheet'
  | 'seat_management'
  | 'beverage_management'
  | 'new_beverage_request'
  | 'staff_leave_request'
  | 'staff_side_dish_request';

export type AdminMenu = {
  id: AdminMenuId;
  label: string;
};

export const ADMIN_MENUS: AdminMenu[] = [
  { id: 'grid', label: '관리자 페이지' },
  { id: 'attendance', label: '출석부' },
  { id: 'staff-work', label: '근무표' },
  { id: 'staff-page', label: '스텝페이지' },
];

export const STAFF_MENUS: AdminMenu[] = [
  { id: 'attendance', label: '출석부' },
  { id: 'staff-work', label: '근무표' },
  { id: 'staff-page', label: '스텝페이지' },
];

export function resolveAdminMenuId(value: string | null): AdminMenuId {
  if (
    value === 'register'
    || value === 'status'
    || value === 'vacation_history'
    || value === 'other_leave_request'
    || value === 'fixed_leave_management'
    || value === 'daily_leave_status'
    || value === 'beverage_serving_sheet'
    || value === 'seat_management'
    || value === 'beverage_management'
    || value === 'new_beverage_request'
    || value === 'staff_leave_request'
    || value === 'staff_side_dish_request'
  ) {
    return value;
  }

  return ADMIN_MENUS.some((menu) => menu.id === value) ? (value as AdminMenuId) : 'grid';
}

export function getAdminMenuLabel(menuId: AdminMenuId): string {
  if (menuId === 'register') {
    return '사원 등록';
  }

  if (menuId === 'status') {
    return '사원 현황';
  }

  if (menuId === 'vacation_history') {
    return '사원별 휴가 현황';
  }

  if (menuId === 'other_leave_request') {
    return '사원 기타 휴무 신청';
  }

  if (menuId === 'fixed_leave_management') {
    return '고정 기타 휴무 관리';
  }

  if (menuId === 'daily_leave_status') {
    return '일별 사원 휴무 현황';
  }

  if (menuId === 'beverage_serving_sheet') {
    return '음료 서빙표';
  }

  if (menuId === 'seat_management') {
    return '사원 좌석 관리';
  }

  if (menuId === 'beverage_management') {
    return '음료 관리';
  }

  if (menuId === 'new_beverage_request') {
    return '새로운 음료 신청';
  }

  if (menuId === 'staff_leave_request') {
    return '스탭 휴무 신청';
  }

  if (menuId === 'staff_side_dish_request') {
    return '반찬 신청';
  }

  return ADMIN_MENUS.find((menu) => menu.id === menuId)?.label || '관리자 페이지';
}
