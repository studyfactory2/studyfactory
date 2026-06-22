import { useState } from 'react';

const TODAY = 22;
const DAYS = Array.from({ length: 30 }, (_, index) => index + 1);
const EMPTY_DAYS = Array.from({ length: new Date(2026, 5, 1).getDay() }, (_, index) => index);
const WEEKDAYS = [
  { label: '일', className: 'sunday' },
  { label: '월', className: '' },
  { label: '화', className: '' },
  { label: '수', className: '' },
  { label: '목', className: '' },
  { label: '금', className: '' },
  { label: '토', className: 'saturday' },
];
const DATE_LABELS = ['일', '월', '화', '수', '목', '금', '토'];

type SideDishItem = {
  id: number;
  menuName: string;
  price: string;
};

function getDateClassName(day: number, selectedDate: number) {
  const classNames = ['calendar-day'];
  const weekday = new Date(2026, 5, day).getDay();

  if (day < TODAY) {
    classNames.push('past');
  }

  if (day === selectedDate) {
    classNames.push('selected');
  }

  if (weekday === 0) {
    classNames.push('sunday');
  }

  if (weekday === 6) {
    classNames.push('saturday');
  }

  return classNames.join(' ');
}

function formatSelectedDate(day: number) {
  const weekday = DATE_LABELS[new Date(2026, 5, day).getDay()];
  return `6/${day}(${weekday})`;
}

export function SideDishPanel() {
  const [selectedDate, setSelectedDate] = useState(TODAY);
  const [items, setItems] = useState<SideDishItem[]>([]);
  const selectedDateLabel = formatSelectedDate(selectedDate);
  const totalPrice = items.reduce((sum, item) => sum + Number(item.price || 0), 0);

  const addItem = () => {
    setItems((current) => [...current, { id: Date.now(), menuName: '', price: '' }]);
  };

  const changeItem = (id: number, field: keyof Omit<SideDishItem, 'id'>, value: string) => {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, [field]: value } : item)));
  };

  const removeItem = (id: number) => {
    setItems((current) => current.filter((item) => item.id !== id));
  };

  return (
    <div className="member-panel">
      <section className="member-notice-box">
        <strong>현재 주문중인 반찬집 : 손찬반찬백화점 센텀점</strong>
        <a href="https://web.coupangeats.com/share?storeId=636864&dishId&key=b29e27b7-ff7a-4d28-952a-ef42687665c0">
          쿠팡이츠 바로가기
        </a>
        <p>마감시간까지 최소주문금액 15,000원 미달시, 주문취소됩니다. 개별연락 드릴게요.</p>
      </section>
      <section className="side-dish-calendar">
        <div className="member-calendar-header">
          <button type="button" aria-label="이전 달">‹</button>
          <strong>2026년 6월</strong>
          <button type="button" aria-label="다음 달">›</button>
        </div>
        <div className="member-calendar-grid" aria-label="반찬 신청 날짜 선택">
          {WEEKDAYS.map((day) => (
            <span className={`calendar-weekday ${day.className}`} key={day.label}>
              {day.label}
            </span>
          ))}
          {EMPTY_DAYS.map((day) => (
            <span className="calendar-empty" key={`side-dish-empty-${day}`} />
          ))}
          {DAYS.map((day) => (
            <button
              className={getDateClassName(day, selectedDate)}
              disabled={day < TODAY}
              type="button"
              key={day}
              onClick={() => setSelectedDate(day)}
            >
              {day}
            </button>
          ))}
        </div>
      </section>
      <div className="meal-toggle">
        <button className="active" type="button">점심</button>
        <button type="button">저녁</button>
      </div>
      <section className="member-list-box">
        <div className="section-heading">
          <div>
            <strong>{selectedDateLabel} 점심 반찬 신청</strong>
            <p>당일 10:45AM 마감</p>
          </div>
          <span>합계: {totalPrice.toLocaleString()}원</span>
        </div>
        {items.length === 0 ? (
          <p className="side-dish-empty-text">추가 버튼으로 반찬을 입력해주세요.</p>
        ) : (
          <div className="side-dish-items">
            {items.map((item, index) => (
              <div className="side-dish-item" key={item.id}>
                <label className="member-field">
                  <input
                    aria-label={`반찬명 ${index + 1}`}
                    placeholder="반찬명"
                    value={item.menuName}
                    onChange={(event) => changeItem(item.id, 'menuName', event.target.value)}
                  />
                </label>
                <label className="member-field">
                  <input
                    aria-label={`금액 ${index + 1}`}
                    inputMode="numeric"
                    placeholder="금액"
                    value={item.price}
                    onChange={(event) => changeItem(item.id, 'price', event.target.value.replace(/[^0-9]/g, ''))}
                  />
                </label>
                <button className="side-dish-remove" type="button" aria-label="반찬 항목 삭제" onClick={() => removeItem(item.id)}>
                  ×
                </button>
              </div>
            ))}
          </div>
        )}
        <button className="member-secondary-action side-dish-add-button" type="button" onClick={addItem}>+ 추가</button>
      </section>
      <button className="member-primary-action" type="button">반찬신청</button>
      <section className="member-list-box">
        <strong>{selectedDateLabel} 점심 신청목록</strong>
        <p>아직 신청한 반찬이 없습니다.</p>
      </section>
    </div>
  );
}
