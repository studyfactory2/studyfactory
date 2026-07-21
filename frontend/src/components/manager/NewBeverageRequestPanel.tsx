import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { Branch, MemberBeverageResponse } from '../../types/domain';

type NewBeverageRequestPanelProps = {
  branches: Branch[];
};

type SortMode = 'name' | 'seat';

export function NewBeverageRequestPanel({ branches }: NewBeverageRequestPanelProps) {
  const [beverages, setBeverages] = useState<MemberBeverageResponse[]>([]);
  const [sortMode, setSortMode] = useState<SortMode>('seat');
  const [message, setMessage] = useState<string | null>(null);
  const branch = branches[0] || { id: 1, name: '망미점' };
  const sortedBeverages = useMemo(() => {
    return [...beverages].sort(sortMode === 'seat' ? compareBySeatNumber : compareByName);
  }, [beverages, sortMode]);

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
      setMessage(error instanceof Error ? error.message : '음료 신청 정보를 불러오지 못했습니다.');
    }
  };

  return (
    <div className="new-beverage-panel">
      <header className="new-beverage-header">
        <a href="/managerdashboard?view=staff-page" aria-label="스텝페이지로 돌아가기">
          <BackIcon />
        </a>
        <h2>새로운 음료 신청</h2>
      </header>

      <div className="new-beverage-sort">
        <button className={sortMode === 'name' ? 'active' : ''} type="button" onClick={() => setSortMode('name')}>
          이름순
        </button>
        <button className={sortMode === 'seat' ? 'active' : ''} type="button" onClick={() => setSortMode('seat')}>
          번호순
        </button>
      </div>

      {message ? (
        <p className="new-beverage-empty">{message}</p>
      ) : (
        <div className="new-beverage-list">
          {sortedBeverages.length === 0 ? (
            <p className="new-beverage-empty">신청 내역이 없습니다.</p>
          ) : (
            sortedBeverages.map((beverage) => {
              const drinks = toDrinkPreview(beverage.drinks);
              const notes = Object.entries(beverage.drinkNotes || {});

              return (
                <article className="new-beverage-card" key={beverage.memberId}>
                  <strong>
                    {formatSeatNumber(beverage.seatNumber)}번 {beverage.memberName}
                  </strong>
                  <div>
                    <span>{drinks}</span>
                    {notes.map(([drink, note]) => <small key={drink}>{drink}: {note}</small>)}
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
    return '안먹음';
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

  return compareByName(first, second);
}

function compareByName(first: MemberBeverageResponse, second: MemberBeverageResponse) {
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

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 5-7 7 7 7" />
    </svg>
  );
}
