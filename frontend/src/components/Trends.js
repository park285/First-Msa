import React from 'react';

const Trends = () => {
  return (
    <div className="card trends-card">
      <h3>주간 칼로리 섭취량</h3>
      <div className="chart-placeholder">
        <p>지난 7일간의 칼로리 섭취량 그래프가 여기에 표시됩니다.</p>
        <div style={{ display: 'flex', justifyContent: 'space-around', alignItems: 'flex-end', height: '150px', border: '1px solid #eee', padding: '10px' }}>
          <div style={{ height: '60%', backgroundColor: 'var(--primary-color)', width: '20px', textAlign: 'center', color: 'white' }}>월</div>
          <div style={{ height: '80%', backgroundColor: 'var(--primary-color)', width: '20px', textAlign: 'center', color: 'white' }}>화</div>
          <div style={{ height: '70%', backgroundColor: 'var(--primary-color)', width: '20px', textAlign: 'center', color: 'white' }}>수</div>
          <div style={{ height: '90%', backgroundColor: 'var(--primary-color)', width: '20px', textAlign: 'center', color: 'white' }}>목</div>
          <div style={{ height: '75%', backgroundColor: 'var(--primary-color)', width: '20px', textAlign: 'center', color: 'white' }}>금</div>
          <div style={{ height: '85%', backgroundColor: 'var(--primary-color)', width: '20px', textAlign: 'center', color: 'white' }}>토</div>
          <div style={{ height: '65%', backgroundColor: 'var(--primary-color)', width: '20px', textAlign: 'center', color: 'white' }}>일</div>
        </div>
      </div>
    </div>
  );
};

export default Trends;
