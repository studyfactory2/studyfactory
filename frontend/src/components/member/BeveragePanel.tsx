import { useEffect, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { BeveragePreferenceResponse } from '../../types/domain';
import { Dropdown } from '../common/Dropdown';

type BeverageInputMode = 'menu' | 'custom';

const MENU_DRINK_OPTIONS = [
  { value: '', label: '메뉴를 선택하세요' },
  { value: '선식', label: '선식' },
  { value: '해독쥬스', label: '해독쥬스' },
  { value: '아아', label: '아아' },
  { value: '뜨아', label: '뜨아' },
  { value: '텀아아', label: '텀아아' },
  { value: '텀뜨아', label: '텀뜨아' },
];

function parseDrinks(value: string) {
  return value
    .split(/[,\n]/)
    .map((drink) => drink.trim())
    .filter(Boolean);
}

function uniqueDrinks(drinks: string[]) {
  const seen = new Set<string>();

  return drinks.filter((drink) => {
    if (seen.has(drink)) {
      return false;
    }

    seen.add(drink);
    return true;
  });
}

export function BeveragePanel() {
  const [inputMode, setInputMode] = useState<BeverageInputMode>('menu');
  const [drinkInput, setDrinkInput] = useState('');
  const [drinks, setDrinks] = useState<string[]>([]);
  const [note, setNote] = useState('');
  const [isComposing, setIsComposing] = useState(false);
  const [drinkDropdownOpen, setDrinkDropdownOpen] = useState(false);
  const [initialLoading, setInitialLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void loadDrinks();
  }, []);

  const loadDrinks = async () => {
    setInitialLoading(true);
    try {
      const response = await apiRequest<BeveragePreferenceResponse>('/api/beverages/me');
      setDrinks(parseDrinks(response.drinks));
      setNote(response.notes || '');
    } catch {
      setMessage('저장된 음료를 불러오지 못했습니다.');
    } finally {
      setInitialLoading(false);
    }
  };

  const addDrink = () => {
    const nextDrinks = parseDrinks(drinkInput);

    if (nextDrinks.length === 0) {
      return;
    }

    setDrinks((current) => uniqueDrinks([...current, ...nextDrinks]));
    setDrinkInput('');
    setMessage(null);
  };

  const selectMenuDrink = (value: string) => {
    setDrinkDropdownOpen(false);
    if (!value) {
      return;
    }

    setDrinks((current) => uniqueDrinks([...current, value]));
    setMessage(null);
  };

  const removeDrink = (index: number) => {
    setDrinks((current) => current.filter((_, currentIndex) => currentIndex !== index));
  };

  const saveDrinks = async () => {
    setLoading(true);
    setMessage(null);
    try {
      const response = await apiRequest<BeveragePreferenceResponse>('/api/beverages/me', {
        method: 'PATCH',
        body: JSON.stringify({
          drinkSetting: drinks.join(','),
          drinkNote: note.trim(),
        }),
      });
      setDrinks(parseDrinks(response.drinks));
      setDrinkInput('');
      setNote(response.notes || '');
      setMessage('음료가 저장되었습니다.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '음료 저장에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="member-panel">
      <section className="beverage-intro">
        <strong>
          오늘의 음료
          <BeverageTitleIcon />
        </strong>
        <p>아침에 서빙해드릴 음료를 선택하세요.<br />언제든 변경 가능해요</p>
      </section>
      <div className="beverage-form">
        <div className="beverage-mode-toggle">
          <button className={inputMode === 'menu' ? 'active' : ''} type="button" onClick={() => setInputMode('menu')}>메뉴에서</button>
          <button className={inputMode === 'custom' ? 'active' : ''} type="button" onClick={() => setInputMode('custom')}>직접 입력</button>
        </div>
        {inputMode === 'menu' ? (
          <div className="beverage-menu-row">
            <Dropdown
              classNamePrefix="custom-select"
              label="음료 메뉴"
              open={drinkDropdownOpen}
              options={MENU_DRINK_OPTIONS}
              placeholderClass
              selectedOption={MENU_DRINK_OPTIONS[0]}
              onSelect={selectMenuDrink}
              onToggle={() => setDrinkDropdownOpen((open) => !open)}
            />
          </div>
        ) : (
          <div className="beverage-input-row">
            <input
              aria-label="음료 입력"
              placeholder="음료를 입력하세요"
              value={drinkInput}
              onChange={(event) => setDrinkInput(event.target.value)}
              onCompositionStart={() => setIsComposing(true)}
              onCompositionEnd={() => setIsComposing(false)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !isComposing) {
                  event.preventDefault();
                  addDrink();
                }
              }}
            />
            <button type="button" aria-label="음료 추가" onClick={addDrink}>
              +
            </button>
          </div>
        )}
        <ol className="beverage-list">
          {initialLoading ? (
            <li className="empty">저장된 음료를 불러오는 중입니다.</li>
          ) : drinks.length === 0 ? (
            <li className="empty">입력된 음료가 없습니다.</li>
          ) : (
            drinks.map((drink, index) => (
              <li key={`${drink}-${index}`}>
                <span>{drink}</span>
                <button type="button" aria-label={`${drink} 삭제`} onClick={() => removeDrink(index)}>
                  <TrashIcon />
                </button>
              </li>
            ))
          )}
        </ol>
        <label className="member-field">
          <span>참고사항</span>
          <textarea
            placeholder="음료 관련 요청사항이 있을 경우 적어주세요"
            rows={5}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
        </label>
        <button className="member-primary-action" type="button" disabled={loading} onClick={saveDrinks}>
          {loading ? '저장 중' : '저장하기'}
          <span aria-hidden="true">→</span>
        </button>
        {message && <p className="beverage-message">{message}</p>}
      </div>
    </div>
  );
}

function BeverageTitleIcon() {
  return (
    <svg className="beverage-title-icon" viewBox="0 0 32 32" aria-hidden="true">
      <path d="M9 3c-1.4 1.5-1.4 3 0 4.5s1.4 3 0 4.5" />
      <path d="M16 3c-1.4 1.5-1.4 3 0 4.5s1.4 3 0 4.5" />
      <path d="M23 3c-1.4 1.5-1.4 3 0 4.5s1.4 3 0 4.5" />
      <path d="M7 15h15v6a6 6 0 0 1-6 6h-3a6 6 0 0 1-6-6v-6Z" />
      <path d="M22 17h2.5a3 3 0 0 1 0 6H22" />
      <path d="M8 29h17" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg className="trash-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
      <path d="M10 11v5" />
      <path d="M14 11v5" />
    </svg>
  );
}
