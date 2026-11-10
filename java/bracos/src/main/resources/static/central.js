// Central v1 (Tickets 014 e 015). 🔒
// - Só fala com a própria API (mesma origem, 127.0.0.1). Nada da internet.
// - O navegador NUNCA recebe o token: os botões usam um "crachá" (cookie HttpOnly)
//   que o próprio JavaScript não consegue ler.
// - O crachá nasce da chave de USO ÚNICO que o ícone entrega no endereço (#k=...).
//   A chave some do endereço na hora e a API já troca por outra.
// - Escreve na tela só com textContent (texto puro): resposta da API nunca vira código.
// - Ticket 020: a única ação que ESCREVE (daily) passa por "Revisar" → "Confirmar".
// - Ticket 023: o detector de golpe manda só o TEXTO colado; a API nunca abre o link.
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

async function tratar(resposta) {
  if (resposta.status === 401) {
    mostrarConexao("sem");
    throw new Error("Sem crachá: abra a Central pelo ícone Jarvis 001.");
  }
  if (!resposta.ok) {
    const motivos = {
      400: "Texto inválido (vazio ou grande demais).",
      413: "Pedido grande demais.",
      503: "Ação desligada (pasta não configurada no .env).",
    };
    throw new Error(motivos[resposta.status] || "A API recusou o pedido.");
  }
  return resposta.json();
}

async function pedir(rota) {
  return tratar(await fetch(rota, { cache: "no-store", credentials: "same-origin", headers: CABECALHOS_CENTRAL }));
}

// Ticket 020: único jeito de a Central ESCREVER. Só JSON, mesma origem e crachá.
async function enviar(rota, corpo) {
  return tratar(await fetch(rota, {
    method: "POST",
    cache: "no-store",
    credentials: "same-origin",
    headers: { ...CABECALHOS_CENTRAL, "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  }));
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

// Ticket 021: Sentinela, só avisa. Tudo conferido de novo aqui (número é número, risco é da lista).
function formatarPortas(r) {
  const n = (v) => (Number.isInteger(v) ? v : 0);
  const rede = n(r && r.abertasParaARede);
  const linhas = [rede === 0
    ? "🛡️ Nenhuma porta aberta para a rede. Tudo trancado."
    : `🛡️ ${rede} porta(s) aberta(s) para a rede · ${n(r && r.soNoPc)} só no PC`];
  linhas.push(r && r.jarvisSoNoPc === true ? "✅ API do Jarvis só no 127.0.0.1" : "🚨 API do Jarvis aberta para a rede!");
  const icone = { alto: "🚨", atencao: "⚠️", normal: "•" };
  const lista = Array.isArray(r && r.portas) ? r.portas : [];
  for (const p of lista.slice(0, 12)) {
    const ic = p && Object.hasOwn(icone, p.risco) ? icone[p.risco] : "•";
    linhas.push(`${ic} ${n(p && p.porta)} · ${String(p && p.nota).slice(0, 80)} (${String(p && p.alcance).slice(0, 20)})`);
  }
  linhas.push("A Sentinela só avisa: quem fecha porta é você.");
  return linhas;
}

// Ticket 022: vigia de integridade. As travas mudaram desde que a API ligou?
function formatarIntegridade(r) {
  const n = (v) => (Number.isInteger(v) ? v : 0);
  const desde = r && typeof r.desde === "string" && /^\d{2}:\d{2}$/.test(r.desde) ? r.desde : "--:--";
  const alterados = n(r && r.alterados);
  const linhas = [alterados === 0
    ? `🔏 ${n(r && r.vigiados)} travas iguais desde ${desde} ✅`
    : `🚨 ${alterados} trava(s) mudaram desde ${desde}!`];
  const icone = { igual: "✅", alterado: "✏️", sumiu: "❌", apareceu: "➕", estranho: "⚠️" };
  const lista = Array.isArray(r && r.arquivos) ? r.arquivos : [];
  for (const a of lista.slice(0, 10)) {
    const ic = a && Object.hasOwn(icone, a.estado) ? icone[a.estado] : "•";
    linhas.push(`${ic} ${String(a && a.nome).slice(0, 80)} · ${String(a && a.estado).slice(0, 12)} · ${String(a && a.hash).slice(0, 12)}`);
  }
  linhas.push("Mudou e não foi você? Investigue antes de usar o Jarvis.");
  return linhas;
}

// Ticket 023: detector de golpe. Nível conferido contra a lista; motivos e conselho são textos fixos da API.
const NIVEL_GOLPE = {
  alto: { titulo: "🚨 Muita cara de golpe!", classe: "erro" },
  suspeito: { titulo: "⚠️ Suspeito: desconfie.", classe: "nx" },
  baixo: { titulo: "✅ Não vi sinais fortes de golpe.", classe: "ok" },
};

function formatarGolpe(r) {
  if (!r || !Object.hasOwn(NIVEL_GOLPE, r.nivel)) {
    throw new Error("A API respondeu algo inesperado.");
  }
  const linhas = [NIVEL_GOLPE[r.nivel].titulo];
  const motivos = Array.isArray(r.motivos) ? r.motivos : [];
  for (const m of motivos.slice(0, 8)) {
    linhas.push(`• ${String(m).slice(0, 120)}`);
  }
  linhas.push(`👉 ${String(r.conselho).slice(0, 200)}`);
  linhas.push("O Jarvis não abriu nenhum link: só leu o texto.");
  return linhas;
}

const LIMITE_GOLPE = 4000; // o mesmo limite da API (GolpeRequest)

function contarGolpe() {
  $("golpe-conta").textContent = `${$("golpe-texto").value.length}/${LIMITE_GOLPE}`;
}

function abrirGolpe() {
  $("golpe").hidden = false;
  $("golpe").scrollIntoView({ behavior: "smooth", block: "center" });
  $("golpe-texto").focus();
}

// Não escreve nada no PC: só pergunta à API. Por isso não precisa de "Confirmar".
async function analisarGolpe(botao) {
  const texto = $("golpe-texto").value.trim();
  if (!texto) {
    escrever(["✍️ Cole a mensagem suspeita primeiro."], "nx");
    return;
  }
  botao.disabled = true;
  escrever(["⏳ analisando… (sem abrir link)"], "fu");
  try {
    const r = await enviar("/central/acoes/sentinela/golpe", { texto });
    escrever(formatarGolpe(r), NIVEL_GOLPE[r.nivel].classe);
    document.dispatchEvent(new Event("jarvis:acao")); // o escritório reage na hora
  } catch (e) {
    escrever([`⚠️ ${e instanceof Error ? e.message : "falhou"}`], "erro");
  } finally {
    botao.disabled = false;
  }
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

// ---------- Daily pela Central (Ticket 020): escreve no cofre, então só com Confirmar ----------

const LIMITE_DAILY = 2000; // o mesmo limite da API (DailyRequest)

function contarDaily() {
  $("daily-conta").textContent = `${$("daily-texto").value.length}/${LIMITE_DAILY}`;
  $("daily-cartao").hidden = true; // mudou o texto: precisa revisar de novo
}

function abrirDaily() {
  $("daily").hidden = false;
  $("daily").scrollIntoView({ behavior: "smooth", block: "center" });
  $("daily-texto").focus();
}

function revisarDaily() {
  const texto = $("daily-texto").value.trim();
  if (!texto) {
    escrever(["✍️ Escreva a linha do daily primeiro."], "nx");
    return;
  }
  if (texto.length > LIMITE_DAILY) {
    escrever([`✂️ No máximo ${LIMITE_DAILY} caracteres.`], "nx");
    return;
  }
  $("daily-cartao-texto").textContent = texto;
  $("daily-cartao").hidden = false;
}

function fecharDaily() {
  $("daily-cartao").hidden = true;
  $("daily").hidden = true;
  $("daily-texto").value = "";
  contarDaily();
}

async function confirmarDaily(botao) {
  const texto = $("daily-cartao-texto").textContent; // exatamente o que você revisou
  botao.disabled = true;
  try {
    const r = await enviar("/central/acoes/daily", { texto });
    const arquivo = r && typeof r.arquivo === "string" && /^\d{4}-\d{2}-\d{2}\.md$/.test(r.arquivo) ? r.arquivo : "daily de hoje";
    escrever([`📝 Linha salva no ${arquivo}.`, "O Jarvis do escritório confirma em instantes."], "ok");
    fecharDaily();
    document.dispatchEvent(new Event("jarvis:acao")); // o escritório confere o quadro na hora
  } catch (e) {
    escrever([`⚠️ ${e instanceof Error ? e.message : "falhou"}`], "erro");
  } finally {
    botao.disabled = false;
  }
}

// ---------- Liga tudo ----------

$("bt-pendencias").addEventListener("click", (e) => comando(e.currentTarget, "/central/acoes/pendencias", formatarPendencias));
$("bt-git").addEventListener("click", (e) => comando(e.currentTarget, "/central/acoes/git-status", formatarGit));
$("bt-portas").addEventListener("click", (e) => comando(e.currentTarget, "/central/acoes/sentinela/portas", formatarPortas));
$("bt-travas").addEventListener("click", (e) => comando(e.currentTarget, "/central/acoes/sentinela/integridade", formatarIntegridade));
$("bt-golpe").addEventListener("click", () => { if ($("golpe").hidden) abrirGolpe(); else $("golpe").hidden = true; });
$("golpe-texto").addEventListener("input", contarGolpe);
$("bt-golpe-analisar").addEventListener("click", (e) => analisarGolpe(e.currentTarget));
$("bt-nuvem").addEventListener("click", () => { $("nuvem").hidden = !$("nuvem").hidden; });
$("bt-daily").addEventListener("click", () => { if ($("daily").hidden) abrirDaily(); else fecharDaily(); });
$("daily-texto").addEventListener("input", contarDaily);
$("bt-daily-revisar").addEventListener("click", revisarDaily);
$("bt-daily-confirmar").addEventListener("click", (e) => confirmarDaily(e.currentTarget));
$("bt-daily-cancelar").addEventListener("click", () => { fecharDaily(); escrever(["🚫 Cancelado: nada foi escrito no cofre."], "fu"); });
$("bt-preparar").addEventListener("click", prepararPedido);
$("bt-aceitar").addEventListener("click", aceitarPedido);
$("bt-recusar").addEventListener("click", () => { fecharPedido(); escrever(["🚫 Recusado: nada saiu do PC."], "fu"); });

tictac();
setInterval(tictac, 1000);
conferirApi();
setInterval(conferirApi, 60000); // 1x por minuto: não enche o log de auditoria
conectar();
