import React from 'react';

const AnalysisDisplay = ({ analysis }) => {
  if (!analysis) {
    return (
      <div className="card placeholder-card">
        <h3>AI 분석 결과</h3>
        <p>일기를 작성하고 AI 분석을 요청하면 이곳에 결과가 표시됩니다.</p>
      </div>
    );
  }

  return (
    <div className="card analysis-card">
      <h3>AI 분석 결과</h3>
      <pre>{analysis}</pre>
    </div>
  );
};

export default AnalysisDisplay;