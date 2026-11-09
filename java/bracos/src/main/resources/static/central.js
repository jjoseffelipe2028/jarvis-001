// Central v1 (Tickets 014 e 015). 🔒
// - Só fala com a própria API (mesma origem, 127.0.0.1). Nada da internet.
// - O navegador NUNCA recebe o token: os botões usam um "crachá" (cookie HttpOnly)
//   que o próprio JavaScript não consegue ler.
// - O crachá nasce da chave de USO ÚNICO que o ícone entrega no endereço (#k=...).
//   A chave some do endereço na hora e a API já troca por outra.
// - Escreve na tela só com textContent (texto puro): resposta da API nunca vira código.
"use strict";

const $ = (id) => document.getElementById(id);
const elRelogio = $("relogio");
const elData = $("data");
const elApi = $("api-status");
const elConexao = $("conexao");
const elSaida = $("saida");

const doisDigitos = (n) => String(n).padStart(2, "0");
const CABECALHOS_CENTRAL = { "X-Jarvis-Central": "1" };

// ---------- Relógio e "API online" (rota pública) ----------

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

// ---------- Crachá da Central ----------

function mostrarConexao(estado) {
  const textos = {
    ok: ["🔓 conectada", "ok"],
    sem: ["🔒 abra pelo ícone Jarvis 001", "nx"],
    erro: ["🔒 chave recusada: abra pelo ícone de novo", "erro"],
  };
  const [texto, classe] = textos[estado];
  elConexao.textContent = texto;
  elConexao.className = classe;
}

async function conectar() {
  const achou = /^#k=([0-9a-f]{64})$/.exec(location.hash);
  // Tira a chave do endereço ANTES de usar: ela não fica à vista nem no endereço atual
  history.replaceState(null, "", "/");
  if (!achou) {
    mostrarConexao("sem");
    return;
  }
  try {
    const resposta = await fetch("/central/sessao", {
      method: "POST",
      cache: "no-store",
      credentials: "same-origin",
      headers: { ...CABECALHOS_CENTRAL, "Content-Type": "application/json" },
      body: JSON.stringify({ chave: achou[1] }),
    });
    mostrarConexao(resposta.ok ? "ok" : "erro");
  } catch {
    mostrarConexao("erro");
  }
}

// ---------- Saída dos comandos (sempre texto puro) ----------

function escrever(linhas, classe) {
  elSaida.replaceChildren();
  for (const linha of linhas) {
    const li = document.createElement("li");
    li.textContent = linha;
    if (classe) {
      li.className = classe;
    }
    elSaida.appendChild(li);
  }
}

async function pedir(rota) {
  const resposta = await fetch(rota, { cache: "no-store", credentials: "same-origin", headers: CABECALHOS_CENTRAL });
  if (resposta.status === 401) {
    mostrarConexao("sem");
    throw new Error("Sem crachá: abra a Central pelo ícone Jarvis 001.");
  }
  if (!resposta.ok) {
    throw new Error(resposta.status === 503 ? "Ação desligada (pasta não configurada no .env)." : "A API recusou o pedido.");
  }
  return resposta.json();
}

async function comando(botao, rota, formatar) {
  botao.disabled = true;
  escrever(["⏳ consultando…"], "fu");
  try {
    escrever(formatar(await pedir(rota)));
  } catch (e) {
    escrever([`⚠️ ${e instanceof Error ? e.message : "falhou"}`], "erro");
  } finally {
    botao.disabled = false;
  }
}

function formatarPendencias(r) {
  const lista = Array.isArray(r && r.pendencias) ? r.pendencias : [];
  const total = Number.isInteger(r && r.total) ? r.total : lista.length;
  if (total === 0) {
    return ["✅ Nenhuma pendência nos últimos 7 dias."];
  }
  const linhas = [`📋 ${total} pendência(s). As primeiras:`];
  for (const p of lista.slice(0, 8)) {
    linhas.push(`• ${String(p && p.texto).slice(0, 140)}  (${String(p && p.dia)})`);
  }
  return linhas;
}

function formatarGit(s) {
  if (s && s.limpo === true) {
    return [`🌿 Branch ${String(s.branch)}: tudo commitado ✅`];
  }
  const n = (v) => (Number.isInteger(v) ? v : 0);
  return [
    `🌿 Branch ${String(s && s.branch)}: há trabalho sem commit`,
    `• ${n(s && s.alterados)} alterado(s) · ${n(s && s.novos)} novo(s)`,
    `• ${n(s && s.aFrente)} commit(s) para enviar · ${n(s && s.atras)} para baixar`,
  ];
}

// ---------- Pedido ao 001: nada sai sem "Aceitar" ----------

function prepararPedido() {
  const texto = $("nuvem-texto").value.trim();
  if (!texto) {
    escrever(["✍️ Escreva o pedido primeiro."], "nx");
    return;
  }
  $("cartao-texto").textContent = texto;
  $("cartao").hidden = false;
}

async function aceitarPedido() {
  const texto = $("cartao-texto").textContent;
  try {
    await navigator.clipboard.writeText(texto);
    escrever(["📋 Copiado! Cole no Claude Code.", "Nada foi enviado sozinho: o envio direto chega na Fase 7."], "ok");
  } catch {
    escrever(["⚠️ Não consegui copiar. Selecione o texto do cartão e copie à mão."], "erro");
  }
  fecharPedido();
}

function fecharPedido() {
  $("cartao").hidden = true;
  $("nuvem-texto").value = "";
}

// ---------- Liga tudo ----------

$("bt-pendencias").addEventListener("click", (e) => comando(e.currentTarget, "/central/acoes/pendencias", formatarPendencias));
$("bt-git").addEventListener("click", (e) => comando(e.currentTarget, "/central/acoes/git-status", formatarGit));
$("bt-nuvem").addEventListener("click", () => { $("nuvem").hidden = !$("nuvem").hidden; });
$("bt-preparar").addEventListener("click", prepararPedido);
$("bt-aceitar").addEventListener("click", aceitarPedido);
$("bt-recusar").addEventListener("click", () => { fecharPedido(); escrever(["🚫 Recusado: nada saiu do PC."], "fu"); });

tictac();
setInterval(tictac, 1000);
conferirApi();
setInterval(conferirApi, 60000); // 1x por minuto: não enche o log de auditoria
conectar();
