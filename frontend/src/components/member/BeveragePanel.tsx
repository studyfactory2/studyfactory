import { useEffect, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { BeveragePreferenceResponse } from '../../types/domain';

function parseDrinks(value: string) {
  return value
    .split(/[,\n]/)
    .map((drink) => drink.trim())
    .filter(Boolean);
}

export function BeveragePanel() {
  const [drinkInput, setDrinkInput] = useState('');
  const [drinks, setDrinks] = useState<string[]>([]);
  const [note, setNote] = useState('');
  const [isComposing, setIsComposing] = useState(false);
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

    setDrinks((current) => [...current, ...nextDrinks]);
    setDrinkInput('');
    setMessage(null);
  };

  const removeDrink = (index: number) => {
    setDrinks((current) => current.filter((_, currentIndex) => currentIndex !== index));
  };

  const saveDrinks = async () => {
    if (drinks.length === 0) {
      setMessage('음료를 하나 이상 추가해주세요.');
      return;
    }

    if (!note.trim()) {
      setMessage('참고사항을 입력해주세요.');
      return;
    }

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
      setNote(response.notes);
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
        <strong>음료</strong>
        <p>아침에 서빙해드릴 음료를 자유롭게 입력해주세요. 언제든 변경할 수 있습니다.</p>
      </section>
      <div className="beverage-form">
        <div className="beverage-input-row">
          <input
            aria-label="음료"
            placeholder="예: 선식, 텀블러 아아"
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
          {loading ? '저장 중' : '저장'}
        </button>
        {message && <p className="beverage-message">{message}</p>}
      </div>
    </div>
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
