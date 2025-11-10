// Logs page behaviour: tail + SSE
async function loadTail() {
  try {
    const res = await fetch('/api/logs?lines=500');
    const lines = await res.json();
    const logEl = document.getElementById('log');
    logEl.innerHTML = '';
    for (const l of lines) {
      const d = document.createElement('div'); d.className = 'line'; d.textContent = l; logEl.appendChild(d);
    }
    logEl.scrollTop = logEl.scrollHeight;
  } catch(e) { console.error(e); }
}

function startSSE() {
  const es = new EventSource('/api/logs/stream');
  es.onmessage = function(ev) {
    const logEl = document.getElementById('log');
    const d = document.createElement('div'); d.className='line'; d.textContent = ev.data; logEl.appendChild(d);
    while (logEl.children.length > 2000) logEl.removeChild(logEl.children[0]);
    logEl.scrollTop = logEl.scrollHeight;
  };
  es.onerror = function(e) { console.warn('SSE error', e); es.close(); setTimeout(startSSE,2000); };
}

// initialize
loadTail(); startSSE();
