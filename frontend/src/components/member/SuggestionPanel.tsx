import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { SuggestionResponse } from '../../types/domain';

type SuggestionCategory = SuggestionResponse['category'];
type SuggestionView = 'category' | 'detail' | 'history';

type SuggestionOption = {
  category: SuggestionCategory;
  icon: 'package' | 'book' | 'message' | 'heart';
  label: string;
  description: string;
  examples: string[];
};

type ConfirmTarget = {
  category: SuggestionCategory;
  content: string;
} | null;

const SUGGESTION_OPTIONS: SuggestionOption[] = [
  {
    category: 'SUPPLIES',
    icon: 'package',
    label: '비품 관련',
    description: '사무용품 · 비품 요청',
    examples: ['A4 용지가 떨어졌어요', '남자화장실 휴지가 떨어졌어요', '여자화장실 휴지가 떨어졌어요', '이름스티커 더 필요해요'],
  },
  {
    category: 'STUDY',
    icon: 'book',
    label: '학습 관련',
    description: '교재 · 강의 문의',
    examples: ['강의실 온도가 너무 낮아요', '스터디룸이 너무 시끄러워요', '좌석 조명이 어두워요', '학습 자료 확인이 필요해요'],
  },
  {
    category: 'GENERAL',
    icon: 'message',
    label: '기타 건의',
    description: '자유 건의사항',
    examples: ['공용공간 정리가 필요해요', '와이파이 연결이 불안정해요', '출입 관련 확인이 필요해요', '기타 불편사항이 있어요'],
  },
  {
    category: 'COUNSELING',
    icon: 'heart',
    label: '상담 요청',
    description: '1:1 상담 신청',
    examples: ['학습 상담을 받고 싶어요', '생활 관리 상담이 필요해요', '스케줄 상담을 요청해요', '담당자와 상담하고 싶어요'],
  },
];

export function SuggestionPanel() {
  const [view, setView] = useState<SuggestionView>('category');
  const [selectedOption, setSelectedOption] = useState<SuggestionOption | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<ConfirmTarget>(null);
  const [suggestions, setSuggestions] = useState<SuggestionResponse[]>([]);
  const [historyLoaded, setHistoryLoaded] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const recentSuggestion = useMemo(() => suggestions[0] ?? null, [suggestions]);

  useEffect(() => {
    void loadSuggestions();
  }, []);

  const selectOption = (option: SuggestionOption) => {
    setSelectedOption(option);
    setMessage(null);
    setView('detail');
  };

  const loadSuggestions = async () => {
    if (historyLoaded || historyLoading) {
      return;
    }

    setHistoryLoading(true);
    try {
      const responses = await apiRequest<SuggestionResponse[]>('/api/suggestions/me');
      setSuggestions(responses);
      setHistoryLoaded(true);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '내 건의 내역을 불러오지 못했습니다.');
    } finally {
      setHistoryLoading(false);
    }
  };

  const openHistory = async () => {
    setView('history');
    setMessage(null);
    await loadSuggestions();
  };

  const backToCategories = () => {
    setView('category');
    setSelectedOption(null);
    setMessage(null);
  };

  const openConfirm = (category: SuggestionCategory, content: string) => {
    setMessage(null);
    setConfirmTarget({ category, content });
  };

  const submitSuggestion = async () => {
    if (!confirmTarget) {
      return;
    }

    setLoading(true);
    try {
      await apiRequest('/api/suggestions', {
        method: 'POST',
        body: JSON.stringify(confirmTarget),
      });
      setMessage('건의사항이 전송되었습니다.');
      setHistoryLoaded(false);
      setConfirmTarget(null);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '건의사항 전송에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  if (view === 'history') {
    return (
      <div className="member-panel">
        <section className="member-list-box suggestion-detail">
          <SuggestionPanelHeader title="내 건의 내역" onBack={backToCategories} onHistory={openHistory} showHistoryButton={false} />
          {historyLoading && <p className="suggestion-empty">내역을 불러오는 중입니다.</p>}
          {!historyLoading && message && <p className="suggestion-message error">{message}</p>}
          {!historyLoading && !message && suggestions.length === 0 && <p className="suggestion-empty">등록된 건의사항이 없습니다.</p>}
          {!historyLoading && suggestions.length > 0 && (
            <div className="suggestion-history-list">
              {suggestions.map((suggestion) => (
                <article className="suggestion-history-item" key={suggestion.id}>
                  <div>
                    <strong>{getSuggestionCategoryLabel(suggestion.category)}</strong>
                    <span className={suggestion.isResolved ? 'resolved' : ''}>{suggestion.isResolved ? '처리완료' : '접수중'}</span>
                  </div>
                  <p>{suggestion.content}</p>
                  <time>{formatSuggestionDate(suggestion.createdAt)}</time>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    );
  }

  if (view === 'detail' && selectedOption) {
    return (
      <div className="member-panel">
        <section className="member-list-box suggestion-detail">
          <SuggestionPanelHeader title={selectedOption.label} onBack={backToCategories} onHistory={openHistory} />
          <div className="suggestion-example-grid">
            {selectedOption.examples.map((example) => (
              <button type="button" key={example} onClick={() => openConfirm(selectedOption.category, example)}>
                {example}
              </button>
            ))}
          </div>
          {message && <p className="suggestion-message">{message}</p>}
        </section>
        {confirmTarget && (
          <SuggestionConfirmModal
            content={confirmTarget.content}
            loading={loading}
            onCancel={() => setConfirmTarget(null)}
            onConfirm={submitSuggestion}
          />
        )}
      </div>
    );
  }

  return (
    <div className="member-panel">
      <section className="suggestion-hero">
        <h1>무엇을<br />도와드릴까요?</h1>
        <p>카테고리를 선택하세요</p>
      </section>
      <section className="suggestion-category-section">
        <div className="suggestion-category-grid">
          {SUGGESTION_OPTIONS.map((option, index) => (
            <button className={`suggestion-category-button category-${index}`} type="button" key={option.category} onClick={() => selectOption(option)}>
              <span className="suggestion-category-icon" aria-hidden="true">
                <SuggestionIcon type={option.icon} />
              </span>
              <strong>{option.label}</strong>
              <span>{option.description}</span>
              <em aria-hidden="true">→</em>
            </button>
          ))}
        </div>
      </section>
      <section className="suggestion-recent-section">
        <div className="member-section-title">
          <h2>최근 문의</h2>
          <button type="button" onClick={openHistory}>전체 보기 ›</button>
        </div>
        {historyLoading ? (
          <p className="suggestion-empty">최근 문의를 불러오는 중입니다.</p>
        ) : recentSuggestion ? (
          <button className="suggestion-recent-card" type="button" onClick={openHistory}>
            <span>{recentSuggestion.isResolved ? '답변완료' : '접수중'}</span>
            <strong>{recentSuggestion.content}</strong>
            <small>{formatSuggestionDate(recentSuggestion.createdAt)} ›</small>
          </button>
        ) : (
          <p className="suggestion-empty">최근 문의가 없습니다.</p>
        )}
      </section>
    </div>
  );
}

type SuggestionPanelHeaderProps = {
  title: string;
  onBack: () => void;
  onHistory: () => void;
  showHistoryButton?: boolean;
};

function SuggestionPanelHeader({ title, onBack, onHistory, showHistoryButton = true }: SuggestionPanelHeaderProps) {
  return (
    <div className="suggestion-detail-header">
      <button type="button" aria-label="건의 유형으로 돌아가기" onClick={onBack}>
        ‹
      </button>
      <h2>{title}</h2>
      {showHistoryButton ? (
        <button type="button" onClick={onHistory}>
          내 건의 내역
        </button>
      ) : (
        <span aria-hidden="true" />
      )}
    </div>
  );
}

type SuggestionConfirmModalProps = {
  content: string;
  loading: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

function SuggestionConfirmModal({ content, loading, onCancel, onConfirm }: SuggestionConfirmModalProps) {
  return (
    <div className="suggestion-modal-backdrop" role="presentation">
      <section className="suggestion-modal" role="dialog" aria-modal="true" aria-labelledby="suggestion-confirm-title">
        <h3 id="suggestion-confirm-title">정말 보내겠습니까?</h3>
        <p>{content}</p>
        <div className="suggestion-modal-actions">
          <button type="button" onClick={onCancel} disabled={loading}>
            취소
          </button>
          <button type="button" onClick={onConfirm} disabled={loading}>
            {loading ? '전송 중' : '보내기'}
          </button>
        </div>
      </section>
    </div>
  );
}

function SuggestionIcon({ type }: { type: SuggestionOption['icon'] }) {
  if (type === 'book') {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 7v14" />
        <path d="M3 18a1 1 0 0 1-1-1V5a2 2 0 0 1 2-2h5a3 3 0 0 1 3 3v15a3 3 0 0 0-3-3H3Z" />
        <path d="M21 18h-6a3 3 0 0 0-3 3V6a3 3 0 0 1 3-3h5a2 2 0 0 1 2 2v12a1 1 0 0 1-1 1Z" />
      </svg>
    );
  }

  if (type === 'message') {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M21 11.5a8.4 8.4 0 0 1-9 8.3 8.7 8.7 0 0 1-3.9-.9L3 20l1.2-4.6A8.2 8.2 0 0 1 3 11.5a8.6 8.6 0 0 1 9-8.3 8.6 8.6 0 0 1 9 8.3Z" />
      </svg>
    );
  }

  if (type === 'heart') {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M20.8 8.8c0 5.3-8.8 10.2-8.8 10.2S3.2 14.1 3.2 8.8A4.6 4.6 0 0 1 7.8 4.2c1.9 0 3.3 1 4.2 2.5.9-1.5 2.3-2.5 4.2-2.5a4.6 4.6 0 0 1 4.6 4.6Z" />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z" />
      <path d="m4 7.5 8 4.5 8-4.5" />
      <path d="M12 12v9" />
    </svg>
  );
}

function getSuggestionCategoryLabel(category: SuggestionCategory) {
  return SUGGESTION_OPTIONS.find((option) => option.category === category)?.label || '건의';
}

function formatSuggestionDate(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
}
