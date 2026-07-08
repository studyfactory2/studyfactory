import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { BeveragePreferenceResponse, Branch, MemberBeverageResponse } from '../../types/domain';

type StaffBeverageManagementPanelProps = {
  branches: Branch[];
};

export function StaffBeverageManagementPanel({ branches }: StaffBeverageManagementPanelProps) {
  const [beverages, setBeverages] = useState<MemberBeverageResponse[]>([]);
  const [searchName, setSearchName] = useState('');
  const [expandedMemberId, setExpandedMemberId] = useState<number | null>(null);
  const [drinkDraft, setDrinkDraft] = useState('');
  const [savingMemberId, setSavingMemberId] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const branch = branches[0] || { id: 1, name: '망미점' };
  const visibleBeverages = useMemo(() => {
    const keyword = searchName.trim().toLowerCase();
    const filteredBeverages = keyword
      ? beverages.filter((beverage) => beverage.memberName.toLowerCase().includes(keyword))
      : beverages;

    return [...filteredBeverages].sort(compareBySeatNumber);
  }, [beverages, searchName]);

  useEffect(() => {
    void loadBeverages();
  }, [branch.id]);

  const loadBeverages = async () => {
    try {
      const params = new URLSearchParams({ branchId: String(branch.id) });
      const response = await apiRequest<MemberBeverageResponse[]>(`/api/beverages/members?${params.toString()}`);
      setBeverages(response);
      setMessage(null);
    } catch (error) {
      setBeverages([]);
      setMessage(error instanceof Error ? error.message : '음료 정보를 불러오지 못했습니다.');
    }
  };

  const toggleMember = (memberId: number) => {
    setExpandedMemberId((currentMemberId) => {
      const nextMemberId = currentMemberId === memberId ? null : memberId;
      if (nextMemberId !== currentMemberId) {
        setDrinkDraft('');
        setMessage(null);
      }

      return nextMemberId;
    });
  };

  const addDrink = async (beverage: MemberBeverageResponse) => {
    const nextDrink = drinkDraft.trim();
    if (!nextDrink) {
      setMessage('음료명을 입력해주세요.');
      return;
    }

    setSavingMemberId(beverage.memberId);
    setMessage(null);
    try {
      const response = await apiRequest<BeveragePreferenceResponse>(`/api/beverages/members/${beverage.memberId}`, {
        method: 'POST',
        body: JSON.stringify({
          drinkSetting: nextDrink,
          drinkNote: beverage.notes?.trim() || null,
        }),
      });
      updateBeverage(beverage.memberId, response);
      setDrinkDraft('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '음료 추가에 실패했습니다.');
    } finally {
      setSavingMemberId(null);
    }
  };

  const deleteDrink = async (beverage: MemberBeverageResponse, drink: string) => {
    setSavingMemberId(beverage.memberId);
    setMessage(null);
    try {
      const params = new URLSearchParams({ drinkSetting: drink });
      const response = await apiRequest<BeveragePreferenceResponse>(
        `/api/beverages/members/${beverage.memberId}/items?${params.toString()}`,
        { method: 'DELETE' },
      );
      updateBeverage(beverage.memberId, response);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '음료 삭제에 실패했습니다.');
    } finally {
      setSavingMemberId(null);
    }
  };

  const updateBeverage = (memberId: number, response: BeveragePreferenceResponse) => {
    setBeverages((currentBeverages) =>
      currentBeverages.map((beverage) =>
        beverage.memberId === memberId
          ? { ...beverage, drinks: response.drinks, notes: response.notes }
          : beverage,
      ),
    );
  };

  return (
    <div className="staff-beverage-panel">
      <header className="staff-beverage-header">
        <a href="/managerdashboard?view=staff-page" aria-label="스텝페이지로 돌아가기">
          <BackIcon />
        </a>
        <h2>음료 관리</h2>
      </header>

      <div className="staff-beverage-tools">
        <span>{branch.name}</span>
        <label className="staff-beverage-search">
          <SearchIcon />
          <input
            value={searchName}
            placeholder="이름 검색"
            onChange={(event) => setSearchName(event.target.value)}
          />
        </label>
      </div>

      {message ? (
        <p className="staff-beverage-empty">{message}</p>
      ) : (
        <div className="staff-beverage-list">
          {visibleBeverages.length === 0 ? (
            <p className="staff-beverage-empty">검색 결과가 없습니다.</p>
          ) : (
            visibleBeverages.map((beverage) => {
              const expanded = expandedMemberId === beverage.memberId;
              return (
                <article
                  className={`staff-beverage-card${expanded ? ' expanded' : ''}`}
                  key={beverage.memberId}
                  onClick={(event) => {
                    if (isInteractiveElement(event.target)) {
                      return;
                    }

                    toggleMember(beverage.memberId);
                  }}
                >
                  <button
                    className="staff-beverage-summary"
                    type="button"
                    aria-expanded={expanded}
                    onClick={() => toggleMember(beverage.memberId)}
                  >
                    <span className="staff-beverage-seat">{formatSeatNumber(beverage.seatNumber)}</span>
                    <span className="staff-beverage-member">
                      <strong>{beverage.memberName}</strong>
                      <small>{toDrinkPreview(beverage.drinks)}</small>
                      {hasNote(beverage.notes) ? <em>참고사항: {beverage.notes}</em> : null}
                    </span>
                    <ChevronIcon />
                  </button>
                  <div className="staff-beverage-detail" hidden={!expanded}>
                    <label className="staff-beverage-input-label" htmlFor={`drink-${beverage.memberId}`}>
                      음료 입력
                    </label>
                    <div className="staff-beverage-input-row">
                      <input
                        id={`drink-${beverage.memberId}`}
                        value={expanded ? drinkDraft : ''}
                        placeholder="음료명을 입력하세요"
                        disabled={!expanded || savingMemberId === beverage.memberId}
                        onChange={(event) => setDrinkDraft(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault();
                            void addDrink(beverage);
                          }
                        }}
                      />
                      <button
                        type="button"
                        aria-label="음료 추가"
                        disabled={!expanded || savingMemberId === beverage.memberId}
                        onClick={() => void addDrink(beverage)}
                      >
                        +
                      </button>
                    </div>
                    <ol className="staff-beverage-items">
                      {parseDrinks(beverage.drinks).length === 0 ? (
                        <li className="empty">입력된 음료가 없습니다.</li>
                      ) : (
                        parseDrinks(beverage.drinks).map((drink, index) => (
                          <li key={`${beverage.memberId}-${drink}-${index}`}>
                            <span>{index + 1}. {drink}</span>
                            <button
                              type="button"
                              aria-label={`${drink} 삭제`}
                              disabled={!expanded || savingMemberId === beverage.memberId}
                              onClick={() => void deleteDrink(beverage, drink)}
                            >
                              <TrashIcon />
                            </button>
                          </li>
                        ))
                      )}
                    </ol>
                  </div>
                </article>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}

function toDrinkPreview(drinks: string) {
  const drinkItems = parseDrinks(drinks);

  if (drinkItems.length === 0) {
    return '입력 없음';
  }

  return drinkItems.join(', ');
}

function parseDrinks(drinks: string) {
  return drinks
    .split(/\r?\n|,/)
    .map((drink) => drink.trim())
    .filter(Boolean);
}

function compareBySeatNumber(first: MemberBeverageResponse, second: MemberBeverageResponse) {
  const firstSeatNumber = toSortableSeatNumber(first.seatNumber);
  const secondSeatNumber = toSortableSeatNumber(second.seatNumber);

  if (firstSeatNumber !== secondSeatNumber) {
    return firstSeatNumber - secondSeatNumber;
  }

  return first.memberName.localeCompare(second.memberName, 'ko');
}

function formatSeatNumber(seatNumber?: number | null) {
  if (!seatNumber || seatNumber < 1) {
    return '-';
  }

  return seatNumber;
}

function toSortableSeatNumber(seatNumber?: number | null) {
  if (!seatNumber || seatNumber < 1) {
    return Number.MAX_SAFE_INTEGER;
  }

  return seatNumber;
}

function hasNote(note?: string | null) {
  return Boolean(note && note.trim() && note.trim() !== '입력 없음');
}

function isInteractiveElement(target: EventTarget) {
  return target instanceof HTMLElement
    && Boolean(target.closest('button, input, textarea, select, a, label'));
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 5-7 7 7 7" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  );
}

function ChevronIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m9 6 6 6-6 6" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
      <path d="M10 11v5" />
      <path d="M14 11v5" />
    </svg>
  );
}
