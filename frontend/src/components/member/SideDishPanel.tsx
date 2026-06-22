export function SideDishPanel() {
  return (
    <div className="member-panel">
      <section className="member-notice-box">
        <strong>현재 주문중인 반찬집 : 손찬반찬백화점 센텀점</strong>
        <a href="https://web.coupangeats.com/share?storeId=636864&dishId&key=b29e27b7-ff7a-4d28-952a-ef42687665c0">
          쿠팡이츠 바로가기
        </a>
        <p>마감시간까지 최소주문금액 15,000원 미달시, 주문취소됩니다. 개별연락 드릴게요.</p>
      </section>
      <div className="meal-toggle">
        <button className="active" type="button">점심</button>
        <button type="button">저녁</button>
      </div>
      <section className="member-list-box">
        <div className="section-heading">
          <div>
            <strong>6/22(월) 점심 반찬 신청</strong>
            <p>당일 10:45AM 마감</p>
          </div>
          <span>합계: 0원</span>
        </div>
        <div className="empty-dashed">추가 버튼으로 반찬을 입력해주세요.</div>
        <button className="member-secondary-action" type="button">+ 추가</button>
      </section>
      <button className="member-primary-action" type="button">반찬신청</button>
      <section className="member-list-box">
        <strong>6/22(월) 점심 신청목록</strong>
        <p>아직 신청한 반찬이 없습니다.</p>
      </section>
    </div>
  );
}
