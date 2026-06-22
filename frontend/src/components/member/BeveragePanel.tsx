export function BeveragePanel() {
  return (
    <div className="member-panel">
      <section className="beverage-intro">
        <strong>음료</strong>
        <p>아침에 서빙해드릴 음료를 자유롭게 입력해주세요. 언제든 변경할 수 있습니다.</p>
      </section>
      <div className="beverage-form">
        <label className="member-field">
          <span>음료</span>
          <input placeholder="예: 선식, 텀블러 아아" />
        </label>
        <label className="member-field">
          <span>참고사항</span>
          <textarea placeholder="음료 관련 요청사항이 있을 경우 적어주세요" rows={5} />
        </label>
        <button className="member-primary-action" type="button">
          저장
        </button>
      </div>
      <section className="member-list-box">
        <strong>등록된 음료</strong>
        <p>아직 등록된 음료가 없습니다.</p>
      </section>
    </div>
  );
}
