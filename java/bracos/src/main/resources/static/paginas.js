// Central v4 (Ticket 039): páginas por área e conversa com a IA local. 🔒
// - Usa SÓ o pedir() e o enviar() da central.js: mesmo crachá, mesma origem, nenhum pedido novo "por fora".
// - Tudo que vem da API ou da IA entra na tela com textContent (texto puro): nunca vira código.
// - A IA só conversa. Ação de verdade continua nos botões, que passam pela lista fechada da API.
// - Nada é guardado no navegador: a conversa some ao fechar a janela.
"use strict";

(() => {
  const el = (id) => document.getElementById(id);
  const PAGINAS = ["inicio", "cyber", "videos", "jogos"];

  // ---------- Páginas ----------

  function abrirPagina(nome) {
    if (!PAGINAS.includes(nome)) {
      return;
    }
    for (const p of PAGINAS) {
      el(`pg-${p}`).hidden = p !== nome;
      el(`menu-${p}`).setAttribute("aria-current", p === nome ? "page" : "false");
    }
    window.scrollTo({ top: 0 });
  }

  for (const p of PAGINAS) {
    el(`menu-${p}`).addEventListener("click", () => abrirPagina(p));
  }
  // Os cartões da página inicial também levam para a área
  for (const cartao of document.querySelectorAll("[data-pagina]")) {
    cartao.addEventListener("click", () => abrirPagina(cartao.dataset.pagina));
  }

  function escreverEm(lista, linhas, classe) {
    lista.replaceChildren();
    for (const linha of linhas) {
      const li = document.createElement("li");
      li.textContent = linha;
      if (classe) {
        li.className = classe;
      }
      lista.appendChild(li);
    }
  }

  const erroDe = (e) => [`⚠️ ${e instanceof Error ? e.message : "falhou"}`];

  // ---------- Página Cyber ----------

  const saidaCyber = el("cyber-saida");

  async function consultar(botao, rota, formatar) {
    botao.disabled = true;
    escreverEm(saidaCyber, ["⏳ consultando…"], "fu");
    try {
      escreverEm(saidaCyber, formatar(await pedir(rota)));
      document.dispatchEvent(new Event("jarvis:acao"));
    } catch (e) {
      escreverEm(saidaCyber, erroDe(e), "erro");
    } finally {
      botao.disabled = false;
    }
  }

  // Ticket 034: tudo conferido de novo aqui (número é número, estado é da lista).
  function formatarIsca(r) {
    if (!r || r.ligada !== true) {
      return ["🪤 Isca desligada.", "Para ligar: ponha JARVIS_ISCA_TOKEN (um token FALSO) no .env e reinicie a API."];
    }
    const toques = Number.isInteger(r.toques) ? r.toques : 0;
    const quando = typeof r.ultimoToque === "string" && /^\d{2}\/\d{2} \d{2}:\d{2}$/.test(r.ultimoToque) ? r.ultimoToque : "";
    const linhas = [toques === 0
      ? "🪤 Isca ligada e quieta: ninguém tocou. ✅"
      : `🚨 A isca foi tocada ${toques} vez(es)! Último toque: ${quando || "sem hora"}.`];
    const arquivo = { sem: "• Arquivo-isca: não configurado.", igual: "✅ Arquivo-isca igual.",
      mexido: "🚨 O arquivo-isca foi mexido!", sumiu: "🚨 O arquivo-isca sumiu!" };
    linhas.push(Object.hasOwn(arquivo, r.arquivo) ? arquivo[r.arquivo] : "• Arquivo-isca: estado desconhecido.");
    if (toques > 0) {
      linhas.push("Alguém usou o token falso. Veja o log da API e troque o token de verdade.");
    }
    return linhas;
  }

  function formatarProposta(r) {
    const linhas = formatarGolpe(r); // mesmo formato do detector (nível, motivos, conselho)
    linhas.pop(); // tira a frase do link: aqui entram os passos
    const passos = Array.isArray(r.passos) ? r.passos : [];
    if (passos.length) {
      linhas.push("Antes de fechar qualquer parceria:");
      passos.slice(0, 6).forEach((p, i) => linhas.push(`${i + 1}. ${String(p).slice(0, 160)}`));
    }
    return linhas;
  }

  async function analisar(botao, rota, corpo, formatar) {
    if (!corpo.texto) {
      escreverEm(saidaCyber, ["✍️ Cole o texto primeiro."], "nx");
      return;
    }
    botao.disabled = true;
    escreverEm(saidaCyber, ["⏳ analisando… (sem abrir link)"], "fu");
    try {
      const r = await enviar(rota, corpo);
      escreverEm(saidaCyber, formatar(r), NIVEL_GOLPE[r.nivel].classe);
      document.dispatchEvent(new Event("jarvis:acao"));
    } catch (e) {
      escreverEm(saidaCyber, erroDe(e), "erro");
    } finally {
      botao.disabled = false;
    }
  }

  el("cy-portas").addEventListener("click", (e) => consultar(e.currentTarget, "/central/acoes/sentinela/portas", formatarPortas));
  el("cy-travas").addEventListener("click", (e) => consultar(e.currentTarget, "/central/acoes/sentinela/integridade", formatarIntegridade));
  el("cy-isca").addEventListener("click", (e) => consultar(e.currentTarget, "/central/acoes/sentinela/isca", formatarIsca));
  el("cy-golpe").addEventListener("click", (e) => analisar(e.currentTarget, "/central/acoes/sentinela/golpe",
    { texto: el("cy-texto").value.trim() }, formatarGolpe));
  el("cy-proposta").addEventListener("click", (e) => {
    const corpo = { texto: el("cy-texto").value.trim() };
    const remetente = el("cy-remetente").value.trim();
    const marca = el("cy-marca").value.trim();
    if (remetente) corpo.remetente = remetente;
    if (marca) corpo.marca = marca;
    analisar(e.currentTarget, "/central/acoes/sentinela/proposta", corpo, formatarProposta);
  });

  // ---------- Conversa com a IA local ----------

  const falas = el("chat-falas");
  const campo = el("chat-texto");
  const MAXIMO_HISTORICO = 8; // o mesmo limite da API (ChatRequest)
  let historico = [];

  function mostrarFala(quem, texto, classe) {
    const li = document.createElement("li");
    const nome = document.createElement("b");
    nome.textContent = quem;
    li.appendChild(nome);
    li.appendChild(document.createTextNode(` ${texto}`));
    li.className = classe;
    falas.appendChild(li);
    falas.scrollTop = falas.scrollHeight;
    return li;
  }

  // Ticket 046: o chat também pede ações. A API só devolve o NOME (regras fixas, sem IA);
  // aqui o nome vira uma das rotas que já existem, por esta tabela fechada. Nome fora da tabela = nada acontece.
  const ACOES_DO_CHAT = {
    pendencias: ["/central/acoes/pendencias", formatarPendencias],
    git_status: ["/central/acoes/git-status", formatarGit],
    portas: ["/central/acoes/sentinela/portas", formatarPortas],
    integridade: ["/central/acoes/sentinela/integridade", formatarIntegridade],
    isca: ["/central/acoes/sentinela/isca", formatarIsca],
  };
  const NOMES_DAS_PAGINAS = { inicio: "Início", cyber: "Cyber", videos: "Vídeos", jogos: "Jogos" };

  function mostrarLinhas(linhas, classe) {
    const li = mostrarFala("Jarvis:", "", classe);
    for (const linha of linhas.slice(0, 12)) {
      li.appendChild(document.createElement("br"));
      li.appendChild(document.createTextNode(String(linha).slice(0, 300)));
    }
    falas.scrollTop = falas.scrollHeight;
  }

  async function fazerAcao(r) {
    if (r.acao === "pagina") {
      if (!Object.hasOwn(NOMES_DAS_PAGINAS, r.pagina)) {
        throw new Error("Página desconhecida.");
      }
      abrirPagina(r.pagina);
      mostrarFala("Jarvis:", `📂 Abri a página ${NOMES_DAS_PAGINAS[r.pagina]}.`, "chat-ia");
      return;
    }
    if (r.acao === "golpe") {
      const g = await enviar("/central/acoes/sentinela/golpe", { texto: String(r.texto || "").slice(0, 4000) });
      mostrarLinhas(formatarGolpe(g), NIVEL_GOLPE[g.nivel].classe);
      return;
    }
    if (!Object.hasOwn(ACOES_DO_CHAT, r.acao)) {
      throw new Error("Ação desconhecida.");
    }
    const [rota, formatar] = ACOES_DO_CHAT[r.acao];
    mostrarLinhas(formatar(await pedir(rota)), "chat-ia");
  }

  async function conversar(botao) {
    const texto = campo.value.trim();
    if (!texto) {
      return;
    }
    botao.disabled = true;
    campo.value = "";
    mostrarFala("Você:", texto, "chat-eu");
    const espera = mostrarFala("Jarvis:", "pensando… (IA local, pode levar alguns segundos)", "fu");
    try {
      const r = await enviar("/central/acoes/chat", { texto, historico });
      if (r && typeof r.acao === "string") {
        espera.remove();
        await fazerAcao(r); // ação não entra no histórico da IA
        document.dispatchEvent(new Event("jarvis:acao"));
        return;
      }
      const resposta = String(r && r.resposta ? r.resposta : "").slice(0, 2100);
      if (!resposta) {
        throw new Error("A IA não respondeu.");
      }
      espera.remove();
      mostrarFala("Jarvis:", resposta, "chat-ia");
      historico.push({ papel: "user", texto }, { papel: "assistant", texto: resposta.slice(0, 2000) });
      historico = historico.slice(-MAXIMO_HISTORICO);
      document.dispatchEvent(new Event("jarvis:acao"));
    } catch (e) {
      espera.remove();
      const motivo = e instanceof Error && e.message.startsWith("Ação desligada")
        ? "A IA local está desligada ou ocupada. Abra o Ollama e tente de novo."
        : (e instanceof Error ? e.message : "falhou");
      mostrarFala("⚠️", motivo, "erro");
    } finally {
      botao.disabled = false;
      campo.focus();
    }
  }

  el("bt-chat").addEventListener("click", (e) => conversar(e.currentTarget));
  campo.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) { // Enter envia; Shift+Enter pula linha
      e.preventDefault();
      conversar(el("bt-chat"));
    }
  });
  el("bt-chat-limpar").addEventListener("click", () => {
    historico = [];
    falas.replaceChildren();
  });

  // ---------- Estúdio 001 (Ticket 047): a dupla cria o jogo ----------
  // Revisar → Confirmar: o pedido só sai depois do clique. A tela só mostra o que a API devolve, em texto puro.

  const esTexto = el("es-texto");
  const esCartao = el("es-cartao");
  const esPassos = el("es-passos");
  const esResumo = el("es-resumo");
  let esperando = null;

  function revisarEstudio() {
    const texto = esTexto.value.trim();
    if (!texto) {
      escreverEm(esPassos, ["✍️ Escreva o jogo que você quer primeiro."], "nx");
      return;
    }
    el("es-cartao-texto").textContent = texto;
    esCartao.hidden = false;
  }

  function mostrarEstado(r) {
    const passos = Array.isArray(r && r.passos) ? r.passos.slice(0, 10).map((p) => String(p).slice(0, 200)) : [];
    escreverEm(esPassos, passos.length ? passos : ["…"], r && r.terminou ? (r.ok ? "ok" : "erro") : "fu");
    const resumo = r && typeof r.resumo === "string" ? r.resumo.slice(0, 3000) : "";
    esResumo.textContent = resumo;
    esResumo.hidden = !resumo;
  }

  async function acompanhar() {
    try {
      const r = await pedir("/central/acoes/estudio");
      mostrarEstado(r);
      if (r && r.terminou) {
        clearInterval(esperando);
        esperando = null;
        el("es-revisar").disabled = false;
      }
    } catch (e) {
      escreverEm(esPassos, erroDe(e), "erro");
    }
  }

  async function confirmarEstudio(botao) {
    const texto = el("es-cartao-texto").textContent; // exatamente o que você revisou
    botao.disabled = true;
    try {
      const r = await enviar("/central/acoes/estudio", { texto, revisao: el("es-revisao").checked });
      esCartao.hidden = true;
      esTexto.value = "";
      el("es-revisar").disabled = true;
      mostrarEstado(r);
      document.dispatchEvent(new Event("jarvis:acao"));
      if (!esperando) {
        esperando = setInterval(acompanhar, 4000);
      }
    } catch (e) {
      const motivo = e instanceof Error && e.message.startsWith("Ação desligada")
        ? "O Estúdio está ocupado com outro pedido (ou a pasta workSpace/estudio-jogos não existe)."
        : (e instanceof Error ? e.message : "falhou");
      escreverEm(esPassos, [`⚠️ ${motivo}`], "erro");
    } finally {
      botao.disabled = false;
    }
  }

  el("es-revisar").addEventListener("click", revisarEstudio);
  el("es-confirmar").addEventListener("click", (e) => confirmarEstudio(e.currentTarget));
  el("es-cancelar").addEventListener("click", () => { esCartao.hidden = true; });
  esTexto.addEventListener("input", () => { esCartao.hidden = true; }); // mudou o texto: revisar de novo
})();
