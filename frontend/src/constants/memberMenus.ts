export type MemberMenuId = 'leave-plan' | 'side-dish' | 'suggestion' | 'beverage' | 'weekly-plan';

export type MemberMenu = {
  id: MemberMenuId;
  label: string;
};

export const MEMBER_MENUS: MemberMenu[] = [
  { id: 'weekly-plan', label: '작업계획' },
  { id: 'leave-plan', label: '휴무계획' },
  { id: 'side-dish', label: '반찬신청' },
  { id: 'suggestion', label: '건의' },
  { id: 'beverage', label: '음료' },
];

export function resolveMemberMenuId(value: string | null): MemberMenuId {
  return MEMBER_MENUS.some((menu) => menu.id === value) ? (value as MemberMenuId) : 'weekly-plan';
}

export function getMemberMenuLabel(menuId: MemberMenuId): string {
  return MEMBER_MENUS.find((menu) => menu.id === menuId)?.label || '작업계획';
}
