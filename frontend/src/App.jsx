import { useEffect, useState } from 'react';
import './App.scss';

function App() {
  const [status, setStatus] = useState('checking...');

  useEffect(() => {
    fetch('/api/health')
      .then((res) => res.json())
      .then((data) => setStatus(data.status))
      .catch(() => setStatus('unreachable'));
  }, []);

  return (
    <main className="app">
      <h1>3D Virtual Campus Management Software</h1>
      <p>
        Backend status: <strong>{status}</strong>
      </p>
    </main>
  );
}

export default App;
