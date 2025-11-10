// Load nav fragment into pages
async function loadNav(){
  try{
    const r = await fetch('/nav.html');
    if(r.ok){ const html = await r.text(); document.getElementById('nav-placeholder').innerHTML = html; }
  }catch(e){ console.warn('No se pudo cargar nav fragment', e); }
}

// Call once when the script loads
loadNav();
