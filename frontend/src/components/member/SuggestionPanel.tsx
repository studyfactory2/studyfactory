const SUGGESTION_TYPES = ['비품관련', '학습관련', '기타건의', '상담요청'];

export function SuggestionPanel() {
  return (
    <div className="member-panel">
      <section className="member-list-box">
        <div className="section-heading">
          <div>
            <strong>건의 유형</strong>
            <p>필요한 항목을 선택해주세요.</p>
          </div>
        </div>
        <div className="suggestion-grid">
          {SUGGESTION_TYPES.map((type) => (
            <button type="button" key={type}>
              {type}
            </button>
          ))}
        </div>
      </section>
      <label className="member-field">
        <span>건의 내용</span>
        <textarea placeholder="건의사항을 입력해주세요" rows={7} />
      </label>
      <button className="member-primary-action" type="button">
        등록하기
      </button>
      <section className="member-list-box">
        <strong>내 건의 목록</strong>
        <p>등록된 건의사항이 없습니다.</p>
      </section>
    </div>
  );
}
