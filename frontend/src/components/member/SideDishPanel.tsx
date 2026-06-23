import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { apiRequest } from '../../api/client';
import type { MealType as ApiMealType, SideDishResponse } from '../../types/domain';

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
type MealType = '점심' | '저녁';
type Message = {
  type: 'success' | 'error';
  text: string;
};

type AlertState = {
  title: string;
  description: string;
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

function formatSelectedDateValue(day: number) {
  return `2026-06-${String(day).padStart(2, '0')}`;
}

function toApiMealType(mealType: MealType): ApiMealType {
  return mealType === '점심' ? 'LUNCH' : 'DINNER';
}

function parseSideDishItems(items: string) {
  return items
    .split(/\n/)
    .map((item) => {
      const [menuName, price] = item.split(':').map((value) => value.trim());

      return {
        menuName: menuName || item,
        price: Number(price || 0),
      };
    })
    .filter((item) => item.menuName);
}

function toOrderItems(items: Array<{ menuName: string; price: number }>) {
  return items.map((item) => `${item.menuName}: ${item.price}`).join('\n');
}

function formatCreatedAt(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return {
      date: value,
      time: '',
    };
  }

  return {
    date: `${date.getFullYear()}. ${String(date.getMonth() + 1).padStart(2, '0')}. ${String(date.getDate()).padStart(2, '0')}.`,
    time: `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`,
  };
}

export function SideDishPanel() {
  const [selectedDate, setSelectedDate] = useState(TODAY);
  const [selectedMeal, setSelectedMeal] = useState<MealType>('점심');
  const [items, setItems] = useState<SideDishItem[]>([]);
  const [sideDishes, setSideDishes] = useState<SideDishResponse[]>([]);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [transferChecked, setTransferChecked] = useState(false);
  const [loading, setLoading] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [message, setMessage] = useState<Message | null>(null);
  const [alertState, setAlertState] = useState<AlertState | null>(null);
  const [cancelTarget, setCancelTarget] = useState<SideDishResponse | null>(null);
  const selectedDateLabel = formatSelectedDate(selectedDate);
  const selectedDateValue = formatSelectedDateValue(selectedDate);
  const selectedMealType = toApiMealType(selectedMeal);
  const deadlineText = selectedMeal === '점심' ? '당일 10:45AM 마감' : '당일 16:30PM 마감';
  const totalPrice = items.reduce((sum, item) => sum + Number(item.price || 0), 0);
  const selectedMealSideDishes = sideDishes.filter((sideDish) => sideDish.mealType === selectedMealType);
  const selectedMealOrderTotal = selectedMealSideDishes.reduce((sum, sideDish) => sum + sideDish.totalPrice, 0);
  const latestOrderCreatedAt = selectedMealSideDishes[0] ? formatCreatedAt(selectedMealSideDishes[0].createdAt) : null;

  useEffect(() => {
    void loadSideDishes();
  }, [selectedDateValue]);

  const loadSideDishes = async () => {
    setLoading(true);
    try {
      const responses = await apiRequest<SideDishResponse[]>(`/api/side-dishes/me?date=${selectedDateValue}`);
      setSideDishes(responses);
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '반찬 신청목록을 불러오지 못했습니다.' });
    } finally {
      setLoading(false);
    }
  };

  const addItem = () => {
    setItems((current) => [...current, { id: Date.now(), menuName: '', price: '' }]);
  };

  const changeItem = (id: number, field: keyof Omit<SideDishItem, 'id'>, value: string) => {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, [field]: value } : item)));
  };

  const removeItem = (id: number) => {
    setItems((current) => current.filter((item) => item.id !== id));
  };

  const getValidItems = () => {
    return items
      .map((item) => ({ menuName: item.menuName.trim(), price: Number(item.price) }))
      .filter((item) => item.menuName && item.price > 0);
  };

  const openConfirm = () => {
    const validItems = items
      .map((item) => ({ menuName: item.menuName.trim(), price: Number(item.price) }))
      .filter((item) => item.menuName && item.price > 0);

    if (validItems.length !== items.length || validItems.length === 0) {
      setMessage({ type: 'error', text: '반찬명과 금액을 모두 입력해주세요.' });
      return;
    }

    setTransferChecked(false);
    setMessage(null);
    setConfirmOpen(true);
  };

  const submitSideDishes = async () => {
    const validItems = getValidItems();

    if (!transferChecked) {
      setMessage({ type: 'error', text: '송금완료를 체크해주세요.' });
      return;
    }

    setSubmitLoading(true);
    setMessage(null);
    try {
      const orderTotalPrice = validItems.reduce((sum, item) => sum + item.price, 0);
      await apiRequest<SideDishResponse>('/api/side-dishes', {
        method: 'POST',
        body: JSON.stringify({
          mealDate: selectedDateValue,
          mealType: selectedMealType,
          menuName: toOrderItems(validItems),
          itemPrice: orderTotalPrice,
          totalPrice: orderTotalPrice,
        }),
      });
      setItems([]);
      setConfirmOpen(false);
      setTransferChecked(false);
      setAlertState({
        title: '신청 완료',
        description: '반찬 신청이 완료되었습니다.',
      });
      await loadSideDishes();
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '반찬 신청에 실패했습니다.' });
    } finally {
      setSubmitLoading(false);
    }
  };

  const deleteSideDish = async () => {
    if (!cancelTarget) {
      return;
    }

    setMessage(null);
    try {
      await apiRequest<void>(`/api/side-dishes/${cancelTarget.id}`, {
        method: 'DELETE',
      });
      setCancelTarget(null);
      setAlertState({
        title: '취소 완료',
        description: '반찬 신청이 정상적으로 취소되었습니다.',
      });
      await loadSideDishes();
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '반찬 신청 삭제에 실패했습니다.' });
    }
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
        <button className={selectedMeal === '점심' ? 'active' : ''} type="button" onClick={() => setSelectedMeal('점심')}>
          점심
        </button>
        <button className={selectedMeal === '저녁' ? 'active' : ''} type="button" onClick={() => setSelectedMeal('저녁')}>
          저녁
        </button>
      </div>
      <section className="member-list-box">
        <div className="section-heading">
          <div>
            <strong>{selectedDateLabel} {selectedMeal} 반찬 신청</strong>
            <p>{deadlineText}</p>
            <p className="side-dish-live-total">실시간 공장반찬 주문합계 금액: {selectedMealOrderTotal.toLocaleString()}원</p>
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
      <button className="member-primary-action" type="button" disabled={submitLoading} onClick={openConfirm}>
        {submitLoading ? '신청 중' : '반찬신청'}
      </button>
      {message && <p className={`side-dish-message ${message.type}`}>{message.text}</p>}
      <section className="member-list-box">
        <div className="side-dish-list-header">
          <strong>{selectedDateLabel} {selectedMeal} 신청목록</strong>
          {latestOrderCreatedAt && (
            <span>
              {latestOrderCreatedAt.date}
              <br />
              {latestOrderCreatedAt.time}
            </span>
          )}
        </div>
        {loading ? (
          <p>신청목록을 불러오는 중입니다.</p>
        ) : selectedMealSideDishes.length === 0 ? (
          <p>아직 신청한 반찬이 없습니다.</p>
        ) : (
          <div className="side-dish-history-list">
            {selectedMealSideDishes.map((sideDish, orderIndex) => {
              const orderItems = parseSideDishItems(sideDish.items);
              const createdAt = formatCreatedAt(sideDish.createdAt);

              return (
                <article className="side-dish-history-item" key={sideDish.id}>
                  <div className="side-dish-order-card-header">
                    <strong>{orderIndex + 1}번째 주문</strong>
                    <button type="button" onClick={() => setCancelTarget(sideDish)}>
                      주문취소
                    </button>
                  </div>
                  <span className="side-dish-order-created-at">{createdAt.date} {createdAt.time}</span>
                  <div className="side-dish-order-content">
                    <ol>
                      {orderItems.map((item, index) => (
                        <li key={`${item.menuName}-${index}`}>
                          <span>{item.menuName}</span>
                          <strong>{item.price.toLocaleString()}원</strong>
                        </li>
                      ))}
                    </ol>
                    <span>세트 합계: {sideDish.totalPrice.toLocaleString()}원</span>
                  </div>
                </article>
              );
            })}
            <strong className="side-dish-list-total">합계: {selectedMealOrderTotal.toLocaleString()}원</strong>
          </div>
        )}
      </section>
      {confirmOpen &&
        createPortal(
          <SideDishConfirmModal
            dateLabel={selectedDateLabel}
            meal={selectedMeal}
            items={getValidItems()}
            transferChecked={transferChecked}
            submitLoading={submitLoading}
            onTransferChange={setTransferChecked}
            onClose={() => setConfirmOpen(false)}
            onSubmit={submitSideDishes}
          />,
          document.body
        )}
      {cancelTarget &&
        createPortal(
          <SideDishCancelModal
            onClose={() => setCancelTarget(null)}
            onSubmit={deleteSideDish}
          />,
          document.body
        )}
      {alertState &&
        createPortal(
          <SideDishAlertModal
            title={alertState.title}
            description={alertState.description}
            onClose={() => setAlertState(null)}
          />,
          document.body
        )}
    </div>
  );
}

type SideDishConfirmModalProps = {
  dateLabel: string;
  meal: MealType;
  items: Array<{ menuName: string; price: number }>;
  transferChecked: boolean;
  submitLoading: boolean;
  onTransferChange: (checked: boolean) => void;
  onClose: () => void;
  onSubmit: () => void;
};

function SideDishConfirmModal({
  dateLabel,
  meal,
  items,
  transferChecked,
  submitLoading,
  onTransferChange,
  onClose,
  onSubmit,
}: SideDishConfirmModalProps) {
  const totalPrice = items.reduce((sum, item) => sum + item.price, 0);

  return (
    <div className="side-dish-modal-backdrop" role="presentation">
      <section className="side-dish-modal" role="dialog" aria-modal="true" aria-labelledby="side-dish-modal-title">
        <div className="side-dish-modal-header">
          <h2 id="side-dish-modal-title">{dateLabel} {meal} 반찬 신청</h2>
          <button type="button" onClick={onClose}>닫기</button>
        </div>
        <section className="side-dish-modal-section">
          <h3>1. 주문내용 확인</h3>
          <ol className="side-dish-modal-order-list">
            {items.map((item, index) => (
              <li key={`${item.menuName}-${index}`}>
                <span>{item.menuName}</span>
                <strong>{item.price.toLocaleString()}원</strong>
              </li>
            ))}
          </ol>
          <p className="side-dish-modal-total">총 {totalPrice.toLocaleString()}원</p>
        </section>
        <section className="side-dish-modal-section">
          <h3>2. 계좌이체</h3>
          <p>사장님 카카오페이 또는 신한은행 계좌로 송금해주세요</p>
          <strong>카카오페이: 사장님 카카오페이</strong>
          <strong>계좌정보: 신한 110-498-435650 김지원</strong>
          <label className="side-dish-transfer-check">
            <input type="checkbox" checked={transferChecked} onChange={(event) => onTransferChange(event.target.checked)} />
            <span>송금완료</span>
          </label>
        </section>
        <section className="side-dish-modal-section">
          <h3>3. 신청하기</h3>
          <p>주문내용 확인 및 송금을 완료하셨으면 아래 신청 버튼을 눌러서 신청을 완료해주세요</p>
          <button className="side-dish-modal-submit" type="button" disabled={!transferChecked || submitLoading} onClick={onSubmit}>
            {submitLoading ? '신청 중' : '신청하기'}
          </button>
        </section>
      </section>
    </div>
  );
}

type SideDishCancelModalProps = {
  onClose: () => void;
  onSubmit: () => void;
};

function SideDishCancelModal({ onClose, onSubmit }: SideDishCancelModalProps) {
  return (
    <div className="side-dish-modal-backdrop" role="presentation">
      <section className="side-dish-small-modal" role="dialog" aria-modal="true" aria-labelledby="side-dish-cancel-title">
        <h2 id="side-dish-cancel-title">주문 취소</h2>
        <p>정말 주문을 취소하시겠습니까?</p>
        <div className="side-dish-modal-actions">
          <button className="side-dish-modal-cancel" type="button" onClick={onClose}>닫기</button>
          <button className="side-dish-modal-danger" type="button" onClick={onSubmit}>주문취소</button>
        </div>
      </section>
    </div>
  );
}

type SideDishAlertModalProps = {
  title: string;
  description: string;
  onClose: () => void;
};

function SideDishAlertModal({ title, description, onClose }: SideDishAlertModalProps) {
  return (
    <div className="side-dish-modal-backdrop" role="presentation">
      <section className="side-dish-small-modal" role="alertdialog" aria-modal="true" aria-labelledby="side-dish-alert-title">
        <h2 id="side-dish-alert-title">{title}</h2>
        <p>{description}</p>
        <button className="side-dish-alert-close" type="button" onClick={onClose}>확인</button>
      </section>
    </div>
  );
}
