// Central v0 (Ticket 014). 🔒
// - Só fala com a própria API (mesma origem, 127.0.0.1). Nada da internet.
// - Só usa a rota PÚBLICA /acoes/hora: o navegador nunca recebe nem guarda o token.
// - Escreve na tela só com textContent (texto puro): resposta da API nunca vira código.
"use strict";

const elRelogio = document.getElementById("relogio");
const elData = document.getElementById("data");
const elApi = document.getElementById("api-status");

const doisDigitos = (n) => String(n).padStart(2, "0");

function tictac() {
  const agora = new Date();
  elRelogio.textContent = `${doisDigitos(agora.getHours())}:${doisDigitos(agora.getMinutes())}:${doisDigitos(agora.getSeconds())}`;
}

function mostrarApi(online, dataTexto) {
  elApi.textContent = online ? "● online" : "● desligada";
  elApi.className = online ? "ok" : "erro";
  if (online && typeof dataTexto === "string" && /^\d{2}\/\d{2}\/\d{4}$/.test(dataTexto)) {
    elData.textContent = dataTexto; // data de Brasília, vinda da API
  }
}

async function conferirApi() {
  try {
    const resposta = await fetch("/acoes/hora", { cache: "no-store", credentials: "omit" });
    if (!resposta.ok) {
      mostrarApi(false);
      return;
    }
    const corpo = await resposta.json();
    mostrarApi(true, corpo && corpo.data);
  } catch {
    mostrarApi(false); // API desligada ou fora do ar
  }
}

tictac();
setInterval(tictac, 1000);
conferirApi();
setInterval(conferirApi, 60000); // 1x por minuto: não enche o log de auditoria
