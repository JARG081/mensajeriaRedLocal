// Users page behaviour
async function loadUsers(){
  try{
    const res = await fetch('/api/users');
    if(!res.ok) throw new Error('HTTP ' + res.status);
    const arr = await res.json();
    const tbody = document.querySelector('#users-table tbody'); tbody.innerHTML = '';
    for(const u of arr){
      const tr = document.createElement('tr');
      const tdId = document.createElement('td'); tdId.textContent = u.id; tr.appendChild(tdId);
      const tdUser = document.createElement('td'); tdUser.textContent = u.username; tr.appendChild(tdUser);
      const tdState = document.createElement('td'); tdState.textContent = u.connected ? 'Conectado' : 'Desconectado';
      tdState.className = u.connected ? 'connected' : 'disconnected'; tr.appendChild(tdState);
      tbody.appendChild(tr);
    }
    document.getElementById('status').textContent = 'Cargado ' + arr.length + ' usuarios.';
    document.getElementById('users-table').style.display = '';
  } catch(e){ document.getElementById('status').textContent = 'Error cargando usuarios: ' + e; console.error(e); }
}

// initialize and poll
loadUsers();
setInterval(loadUsers, 10000);
