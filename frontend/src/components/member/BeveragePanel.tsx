import { useEffect, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { BeveragePreferenceResponse } from '../../types/domain';
import { Dropdown } from '../common/Dropdown';

const BASE_DRINK_OPTIONS = [
  { value: '', label: '음료를 선택해주세요' },
  { value: '선식', label: '선식' },
  { value: '해독쥬스', label: '해독쥬스' },
  { value: '아아', label: '아아' },
  { value: '뜨아', label: '뜨아' },
  { value: '텀아아', label: '텀아아' },
  { value: '텀뜨아', label: '텀뜨아' },
];

function parseDrinks(value: string) {
  return value.split(/[,\n]/).map((drink) => drink.trim()).filter(Boolean);
}

function uniqueDrinks(drinks: string[]) {
  return [...new Set(drinks)];
}

export function BeveragePanel() {
  const [inputMode, setInputMode] = useState<'menu' | 'custom'>('menu');
  const [drinkInput, setDrinkInput] = useState('');
  const [drinks, setDrinks] = useState<string[]>([]);
  const [drinkNotes, setDrinkNotes] = useState<Record<string, string>>({});
  const [isComposing, setIsComposing] = useState(false);
  const [drinkDropdownOpen, setDrinkDropdownOpen] = useState(false);
  const [initialLoading, setInitialLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => { void loadDrinks(); }, []);

  const loadDrinks = async () => {
    setInitialLoading(true);
    try {
      const response = await apiRequest<BeveragePreferenceResponse>('/api/beverages/me');
      const nextDrinks = parseDrinks(response.drinks);
      setDrinks(nextDrinks);
      setDrinkNotes(response.drinkNotes || {});
    } catch {
      setMessage('저장된 음료를 불러오지 못했습니다.');
    } finally {
      setInitialLoading(false);
    }
  };

  const addDrinks = (nextDrinks: string[]) => {
    setDrinks((current) => uniqueDrinks([...current, ...nextDrinks]));
    setMessage(null);
  };

  const changeBaseDrink = (value: string) => {
    if (value) {
      addDrinks([value]);
    }
    setDrinkDropdownOpen(false);
  };

  const addDrink = () => {
    const nextDrinks = parseDrinks(drinkInput);
    if (nextDrinks.length > 0) {
      addDrinks(nextDrinks);
      setDrinkInput('');
    }
  };

  const removeDrink = (drink: string) => {
    setDrinks((current) => current.filter((item) => item !== drink));
    setDrinkNotes((current) => {
      const next = { ...current };
      delete next[drink];
      return next;
    });
  };

  const saveDrinks = async () => {
    setLoading(true);
    setMessage(null);
    try {
      const response = await apiRequest<BeveragePreferenceResponse>('/api/beverages/me', {
        method: 'PATCH',
        body: JSON.stringify({ drinkSetting: drinks.join(','), drinkNotes }),
      });
      const nextDrinks = parseDrinks(response.drinks);
      setDrinks(nextDrinks);
      setDrinkInput('');
      setDrinkNotes(response.drinkNotes || {});
      setMessage('음료가 저장되었습니다.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '음료 저장에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="member-panel beverage-preference-panel">
      <section className="beverage-intro beverage-hero">
        <strong>오늘의 음료 <span aria-hidden="true"><CoffeeIcon /></span></strong>
        <p>아침에 서빙해드릴 음료를 선택하세요.<br />언제든 변경 가능해요.</p>
      </section>
      <div className="beverage-form">
        <div className="beverage-mode-tabs" role="tablist" aria-label="음료 입력 방식">
          <button className={inputMode === 'menu' ? 'active' : ''} type="button" role="tab" aria-selected={inputMode === 'menu'} onClick={() => setInputMode('menu')}>메뉴에서</button>
          <button className={inputMode === 'custom' ? 'active' : ''} type="button" role="tab" aria-selected={inputMode === 'custom'} onClick={() => setInputMode('custom')}>직접 입력</button>
        </div>
        {inputMode === 'menu' ? (
          <div className="beverage-menu-picker">
            <span className="beverage-picker-icon" aria-hidden="true"><CoffeeIcon /></span>
            <Dropdown classNamePrefix="custom-select" label="기본 음료" open={drinkDropdownOpen} options={BASE_DRINK_OPTIONS} placeholderClass selectedOption={BASE_DRINK_OPTIONS[0]} onSelect={changeBaseDrink} onToggle={() => setDrinkDropdownOpen((open) => !open)} />
          </div>
        ) : (
          <div className="beverage-input-row beverage-custom-input-row">
            <input aria-label="직접 입력 음료" placeholder="예: 텀블러 아아 (얼음 적게)" value={drinkInput} onChange={(event) => setDrinkInput(event.target.value)} onCompositionStart={() => setIsComposing(true)} onCompositionEnd={() => setIsComposing(false)} onKeyDown={(event) => { if (event.key === 'Enter' && !isComposing) { event.preventDefault(); addDrink(); } }} />
            <button className="beverage-custom-submit" type="button" onClick={addDrink}>+</button>
          </div>
        )}
        <ol className="beverage-note-list">
          {initialLoading ? <li className="empty">저장된 음료를 불러오는 중입니다.</li> : drinks.length === 0 ? <li className="empty">음료를 선택하면 참고사항을 입력할 수 있습니다.</li> : drinks.map((drink) => (
            <li key={drink}>
              <label><strong>{drink}</strong><input aria-label={`${drink} 참고사항`} placeholder="참고사항 없음" value={drinkNotes[drink] || ''} onChange={(event) => setDrinkNotes((current) => ({ ...current, [drink]: event.target.value }))} /></label>
              <button type="button" aria-label={`${drink} 삭제`} onClick={() => removeDrink(drink)}><TrashIcon /></button>
            </li>
          ))}
        </ol>
        <button className="member-primary-action" type="button" disabled={loading} onClick={saveDrinks}>{loading ? '저장 중' : '저장하기 →'}</button>
        {message && <p className="beverage-message">{message}</p>}
      </div>
    </div>
  );
}

function CoffeeIcon() { return <svg className="coffee-line-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 8h13v7a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8Z" /><path d="M17 10h1.5a2.5 2.5 0 0 1 0 5H17" /><path d="M7 5c0-1 1-1.2 1-2.2" /><path d="M11 5c0-1 1-1.2 1-2.2" /><path d="M15 5c0-1 1-1.2 1-2.2" /></svg>; }
function TrashIcon() { return <svg className="trash-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18" /><path d="M8 6V4h8v2" /><path d="M19 6l-1 14H6L5 6" /><path d="M10 11v5" /><path d="M14 11v5" /></svg>; }
