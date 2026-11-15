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

  // Ticket 060: caixa de pedidos para a 001. O chat só MOSTRA o pedido (Revisar);
  // a nota só nasce no clique em Confirmar. Texto em textContent (nunca HTML).
  function revisarPedido001(texto) {
    const pedido = String(texto || "").slice(0, 2000);
    const li = mostrarFala("Jarvis:", "📬 Revise o pedido para a caixa da 001:", "chat-ia");
    const cartao = document.createElement("div");
    cartao.className = "cartao-texto";
    cartao.textContent = pedido;
    const confirmar = document.createElement("button");
    confirmar.type = "button";
    confirmar.className = "aceitar";
    confirmar.textContent = "Confirmar";
    const cancelar = document.createElement("button");
    cancelar.type = "button";
    cancelar.className = "recusar";
    cancelar.textContent = "Cancelar";
    const botoes = document.createElement("div");
    botoes.className = "botoes";
    botoes.append(confirmar, " ", cancelar);
    li.append(cartao, botoes);
    falas.scrollTop = falas.scrollHeight;
    cancelar.addEventListener("click", () => {
      botoes.remove();
      mostrarFala("Jarvis:", "Pedido cancelado. Nada foi gravado.", "fu");
    });
    confirmar.addEventListener("click", async () => {
      confirmar.disabled = true;
      cancelar.disabled = true;
      try {
        const r = await enviar("/central/acoes/pedido-001", { texto: pedido }); // exatamente o que você revisou
        botoes.remove();
        mostrarFala("Jarvis:", `✅ Anotado na caixa da 001 (${String(r.arquivo || "").slice(0, 40)}). `
          + "Quando abrir o Claude Code, ela lê e te mostra o plano antes de fazer.", "chat-ia");
        document.dispatchEvent(new Event("jarvis:acao"));
      } catch (e) {
        confirmar.disabled = false;
        cancelar.disabled = false;
        mostrarFala("⚠️", e instanceof Error ? e.message : "falhou", "erro");
      }
    });
  }

  // ---------- Ticket 061: botões dos super agentes ----------
  // 🛡️ Ronda: comando fixo no Java (sem corpo). A tela só mostra o que volta, em textContent.
  const ICONE_RONDA = { ok: "✅", atencao: "⚠️", alerta: "🚨", falha: "❔" };
  const NOME_RONDA = { ok: "tudo certo", atencao: "atenção", alerta: "ALERTA", falha: "não deu para conferir" };

  function nivelRonda(n) {
    return Object.hasOwn(ICONE_RONDA, n) ? n : "falha";
  }

  async function fazerRonda() {
    const botao = el("bt-ronda");
    const lista = el("ronda-vigias");
    const geral = el("ronda-geral");
    botao.disabled = true;
    botao.textContent = "🛡️ Rondando… (uns 15 s)";
    lista.replaceChildren();
    geral.hidden = true;
    try {
      const r = await enviar("/central/acoes/ronda", {});
      const vigias = Array.isArray(r && r.vigias) ? r.vigias.slice(0, 5) : [];
      for (const v of vigias) {
        const li = document.createElement("li");
        const n = nivelRonda(v.nivel);
        li.className = n === "ok" ? "ok" : n === "alerta" ? "erro" : "nx";
        li.textContent = `${ICONE_RONDA[n]} ${String(v.vigia).slice(0, 20)}: ${String(v.frase).slice(0, 300)}`;
        lista.appendChild(li);
      }
      const n = nivelRonda(r && r.geral);
      geral.textContent = `${ICONE_RONDA[n]} ${NOME_RONDA[n]} · ${String((r && r.frase) || "").slice(0, 300)}`;
      geral.hidden = false;
      document.dispatchEvent(new Event("jarvis:acao"));
      return { geral: n, frase: geral.textContent };
    } finally {
      botao.disabled = false;
      botao.textContent = "🛡️ Ronda do defensor";
    }
  }

  el("bt-ronda").addEventListener("click", () => {
    fazerRonda().catch((e) => {
      const li = document.createElement("li");
      li.className = "erro";
      li.textContent = `⚠️ ${e instanceof Error ? e.message : "falhou"}`;
      el("ronda-vigias").replaceChildren(li);
    });
  });

  // 🎬 Produzir cortes: atalho para o Estúdio de cortes (Ticket 048), que já chama o produtor.py por lista fixa.
  el("bt-cortes").addEventListener("click", () => {
    abrirPagina("inicio");
    el("ct-secao").scrollIntoView({ behavior: "smooth", block: "center" });
    el("ct-video").focus();
  });

  // ---------- Ticket 062: "Bom dia" como jornalzinho ----------
  // Só LÊ: rotas GET que já existem + a última ronda guardada na API. Cada manchete falha sozinha.
  // Texto das pendências vem do cofre (dado de fora): só textContent, cortado.
  const VIGIA_DA_RONDA = { ok: "✅", atencao: "⚠️", alerta: "🚨", falha: "❔" };

  async function manchete(funcao) {
    try {
      return await funcao();
    } catch (e) {
      return "❔ não consegui ler agora";
    }
  }

  function daRonda(ultima, nome) {
    if (!ultima || !ultima.resultado || !Array.isArray(ultima.resultado.vigias)) {
      return "faça a ronda 🛡️ para ver";
    }
    const v = ultima.resultado.vigias.find((x) => x && x.vigia === nome);
    if (!v) {
      return "❔ a última ronda não trouxe";
    }
    const icone = Object.hasOwn(VIGIA_DA_RONDA, v.nivel) ? VIGIA_DA_RONDA[v.nivel] : "❔";
    const quando = /^\d{2}:\d{2}$/.test(String(ultima.quando)) ? ultima.quando : "--:--";
    return `${icone} ${String(v.frase).slice(0, 160)} (ronda das ${quando})`;
  }

  async function abrirJornal() {
    const caixa = el("jornal");
    const lista = el("jornal-manchetes");
    const agora = new Date();
    const h = agora.getHours();
    const saudacao = h >= 5 && h < 12 ? "BOM DIA" : h < 18 && h >= 12 ? "BOA TARDE" : "BOA NOITE";
    el("jornal-titulo").textContent = `📰 ${saudacao}, JOSÉ · `
      + agora.toLocaleDateString("pt-BR", { weekday: "long", day: "2-digit", month: "2-digit" })
      + ` · ${agora.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}`;
    lista.replaceChildren();
    caixa.hidden = false;

    const ultima = pedir("/central/acoes/ronda/ultima").catch(() => null);
    const manchetes = await Promise.all([
      manchete(async () => {
        const r = await pedir("/central/acoes/pendencias");
        const total = Number.isInteger(r && r.total) ? r.total : 0;
        const primeira = Array.isArray(r && r.pendencias) && r.pendencias[0] ? String(r.pendencias[0].texto).slice(0, 120) : "";
        return total === 0 ? "✅ nenhuma nos últimos 7 dias" : `${total} aberta(s) · 1ª: ${primeira}`;
      }),
      manchete(async () => formatarGit(await pedir("/central/acoes/git-status")).slice(0, 2).join(" · ")),
      manchete(async () => {
        const [p, i, s] = await Promise.all([pedir("/central/acoes/sentinela/portas"),
          pedir("/central/acoes/sentinela/integridade"), pedir("/central/acoes/sentinela/isca")]);
        return [formatarPortas(p)[0], formatarIntegridade(i)[0], formatarIsca(s)[0]].join(" · ");
      }),
      ultima.then((u) => daRonda(u, "Duelo")),
      ultima.then((u) => daRonda(u, "Backup")),
    ]);
    const nomes = ["📝 Pendências", "🌿 Git", "🛡️ Sentinela", "⚔️ Duelo", "💾 Backup"];
    nomes.forEach((nome, i) => {
      const dt = document.createElement("dt");
      dt.textContent = nome;
      const dd = document.createElement("dd");
      dd.textContent = String(manchetes[i]).slice(0, 400);
      lista.append(dt, dd);
    });
    document.dispatchEvent(new Event("jarvis:acao"));
  }

  el("bt-jornal").addEventListener("click", (e) => {
    const botao = e.currentTarget;
    botao.disabled = true;
    abrirJornal().finally(() => { botao.disabled = false; });
  });

  async function fazerAcao(r) {
    if (r.acao === "jornal") {
      await abrirJornal();
      mostrarFala("Jarvis:", "📰 Seu jornalzinho do dia está nos Super agentes.", "chat-ia");
      return;
    }
    if (r.acao === "ronda") {
      const espera = mostrarFala("Jarvis:", "🛡️ Fazendo a ronda do defensor (uns 15 s)…", "fu");
      const resultado = await fazerRonda();
      espera.remove();
      mostrarFala("Jarvis:", `${resultado.frase} (detalhes nos Super agentes)`, resultado.geral === "alerta" ? "erro" : "chat-ia");
      return;
    }
    if (r.acao === "pedido001") {
      revisarPedido001(r.texto);
      return;
    }
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
      const modelo = el("chat-modelo").value === "maior" ? "maior" : "pequeno"; // Ticket 065: só os 2 nomes fixos
      const r = await enviar("/central/acoes/chat", { texto, historico, modelo });
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

  // Ticket 065: a opção "maior" só liga se o servidor tiver o modelo configurado (o nome nunca aparece aqui).
  setTimeout(() => {
    pedir("/central/acoes/chat/modelos").then((m) => {
      const opcao = el("chat-modelo").querySelector('option[value="maior"]');
      if (m && m.maior === true) {
        opcao.disabled = false;
        opcao.textContent = "🧠 Gemma maior (mais lento)";
      }
    }).catch(() => {});
  }, 4500); // depois que a Central troca a chave pelo crachá

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

  // ---------- Estúdio de cortes (Ticket 048): vídeo da lista ou link → cortes prontos ----------

  const ctVideo = el("ct-video");
  const ctCartao = el("ct-cartao");
  const ctPassos = el("ct-passos");
  const ctSaida = el("ct-saida");
  const ctDupla = el("ct-dupla-texto");
  const IDIOMA = { "": "sem legenda extra", en: "+ legenda em inglês", es: "+ legenda em espanhol" };
  let ctPedido = null;
  let ctEsperando = null;

  async function carregarVideos() {
    try {
      const r = await pedir("/central/acoes/cortes/videos");
      const videos = Array.isArray(r && r.videos) ? r.videos.slice(0, 30) : [];
      const primeira = ctVideo.options[0];
      ctVideo.replaceChildren(primeira);
      for (const v of videos) {
        const op = document.createElement("option");
        op.value = String(v.nome);
        op.textContent = `${String(v.nome).slice(0, 80)} (${Number.isInteger(v.megas) ? v.megas : "?"} MB)`;
        ctVideo.appendChild(op);
      }
    } catch (e) {
      escreverEm(ctPassos, erroDe(e).concat(["(confira JARVIS_CORTES_ENTRADA e JARVIS_CORTES_SAIDA no .env)"]), "erro");
    }
  }

  function revisarCortes() {
    const video = ctVideo.value;
    const link = el("ct-link").value.trim();
    const arroba = el("ct-arroba").value.trim();
    const top = Number.parseInt(el("ct-top").value, 10);
    const idioma = el("ct-idioma").value;
    if (!video === !link) {
      escreverEm(ctPassos, ["✍️ Escolha um vídeo da lista OU cole um link (só um dos dois)."], "nx");
      return;
    }
    if (link && !el("ct-autorizado").checked) {
      escreverEm(ctPassos, ["✋ Marque que tem autorização do dono do vídeo."], "nx");
      return;
    }
    if (!/^@?[A-Za-z0-9_.]{1,30}$/.test(arroba)) {
      escreverEm(ctPassos, ["✍️ Escreva o @ do dono do vídeo (vai como crédito em todo corte)."], "nx");
      return;
    }
    if (!(top >= 1 && top <= 20)) {
      escreverEm(ctPassos, ["✍️ Quantidade de cortes: de 1 a 20."], "nx");
      return;
    }
    ctPedido = { arroba, top, idioma, dupla: el("ct-dupla").checked };
    if (video) {
      ctPedido.video = video;
    } else {
      ctPedido.link = link;
      ctPedido.autorizado = true;
    }
    el("ct-cartao-texto").textContent = `${video ? "Vídeo: " + video : "Link: " + link} · crédito ${arroba} · ${top} cortes · `
      + `${IDIOMA[idioma] || ""} · ${ctPedido.dupla ? "001 + 002 escolhem os melhores" : "sem revisão da dupla"}`;
    ctCartao.hidden = false;
  }

  function mostrarCortes(r) {
    const limpar = (lista, n) => (Array.isArray(lista) ? lista.slice(0, n).map((x) => String(x).slice(0, 220)) : []);
    const passos = limpar(r && r.passos, 12);
    escreverEm(ctPassos, passos.length ? passos : ["…"], r && r.terminou ? (r.ok ? "ok" : "erro") : "fu");
    escreverEm(ctSaida, limpar(r && r.saida, 12), "fu");
    const dupla = r && typeof r.dupla === "string" ? r.dupla.slice(0, 3000) : "";
    ctDupla.textContent = dupla;
    ctDupla.hidden = !dupla;
  }

  async function acompanharCortes() {
    try {
      const r = await pedir("/central/acoes/cortes");
      mostrarCortes(r);
      if (r && r.terminou) {
        clearInterval(ctEsperando);
        ctEsperando = null;
        el("ct-revisar").disabled = false;
      }
    } catch (e) {
      escreverEm(ctPassos, erroDe(e), "erro");
    }
  }

  async function confirmarCortes(botao) {
    if (!ctPedido) {
      return;
    }
    botao.disabled = true;
    try {
      const r = await enviar("/central/acoes/cortes", ctPedido);
      ctCartao.hidden = true;
      el("ct-revisar").disabled = true;
      mostrarCortes(r);
      document.dispatchEvent(new Event("jarvis:acao"));
      if (!ctEsperando) {
        ctEsperando = setInterval(acompanharCortes, 5000);
      }
    } catch (e) {
      const motivo = e instanceof Error && e.message.startsWith("Ação desligada")
        ? "O Estúdio de cortes está ocupado (ou as pastas de cortes não estão no .env)."
        : (e instanceof Error ? e.message : "falhou");
      escreverEm(ctPassos, [`⚠️ ${motivo}`], "erro");
    } finally {
      botao.disabled = false;
    }
  }

  el("ct-atualizar").addEventListener("click", carregarVideos);
  el("ct-revisar").addEventListener("click", revisarCortes);
  el("ct-confirmar").addEventListener("click", (e) => confirmarCortes(e.currentTarget));
  el("ct-cancelar").addEventListener("click", () => { ctCartao.hidden = true; });
  for (const id of ["ct-video", "ct-link", "ct-arroba", "ct-top", "ct-idioma", "ct-dupla", "ct-autorizado"]) {
    el(id).addEventListener("input", () => { ctCartao.hidden = true; });
  }
  ctVideo.addEventListener("focus", () => { if (ctVideo.options.length <= 1) carregarVideos(); });

  // ---------- Vídeo do Mestre / Brabo (Ticket 052): tema → roteiro da dupla → vídeo na tela ----------

  const brPersonagem = el("br-personagem");
  const brCorte = el("br-corte");
  const brCartao = el("br-cartao");
  const brPassos = el("br-passos");
  const brSaida = el("br-saida");
  const brPronto = el("br-pronto");
  const brPlayer = el("br-player");
  const NOME_VIDEO = /^[A-Za-z0-9._-]{1,120}-(brabo|mestre)\.mp4$/;
  let brPedido = null;
  let brEsperando = null;
  let brUrl = null;
  let brMostrado = "";

  async function carregarCortesBrabo() {
    try {
      const r = await pedir("/central/acoes/brabo/cortes");
      const cortes = Array.isArray(r && r.cortes) ? r.cortes.slice(0, 30) : [];
      const primeira = brCorte.options[0];
      brCorte.replaceChildren(primeira);
      for (const nome of cortes) {
        const op = document.createElement("option");
        op.value = String(nome);
        op.textContent = String(nome).slice(0, 80);
        brCorte.appendChild(op);
      }
    } catch (e) {
      escreverEm(brPassos, erroDe(e).concat(["(confira JARVIS_CORTES_SAIDA no .env)"]), "erro");
    }
  }

  function trocarPersonagem() {
    const brabo = brPersonagem.value === "brabo";
    el("br-so-brabo").hidden = !brabo;
    el("br-tema").placeholder = brabo ? "Tema (opcional): ex. vacilo na fuga" : "Tema: ex. como trocar a resistência do chuveiro";
    brCartao.hidden = true;
    if (brabo && brCorte.options.length <= 1) carregarCortesBrabo();
  }

  function revisarBrabo() {
    const personagem = brPersonagem.value;
    // Ticket 053: sem tema, o Brabo narra o que as fotos da jogada mostram
    const tema = el("br-tema").value.trim() || (personagem === "brabo" ? "comente a jogada" : "");
    if (!tema || tema.length > 300) {
      escreverEm(brPassos, ["✍️ Escreva o tema do vídeo (até 300 letras)."], "nx");
      return;
    }
    brPedido = { personagem, tema, dupla: el("br-dupla").checked };
    let texto = `${personagem === "mestre" ? "Mestre Parafuso" : "Brabo"} · tema: ${tema}`;
    if (personagem === "brabo") {
      const corte = brCorte.value;
      const arroba = el("br-arroba").value.trim();
      if (!corte) {
        escreverEm(brPassos, ["✍️ Escolha o corte para o Brabo comentar."], "nx");
        return;
      }
      // o escolhido e os de baixo dele na lista (até 8)
      const quantos = Math.min(8, Math.max(1, parseInt(el("br-quantos").value, 10) || 1));
      const cortes = Array.from(brCorte.options).slice(brCorte.selectedIndex, brCorte.selectedIndex + quantos)
        .map((op) => op.value).filter(Boolean);
      if (!/^@?[A-Za-z0-9_.]{1,30}$/.test(arroba)) {
        escreverEm(brPassos, ["✍️ Escreva o @ do dono do vídeo (com autorização dele)."], "nx");
        return;
      }
      brPedido.cortes = cortes;
      brPedido.arroba = arroba;
      texto += cortes.length > 1
        ? ` · ${cortes.length} cortes: ${cortes[0]} … ${cortes[cortes.length - 1]} · crédito ${arroba}`
        : ` · corte: ${corte} · crédito ${arroba}`;
    }
    el("br-cartao-texto").textContent = `${texto} · ${brPedido.dupla ? "002 revisa" : "sem revisão da 002"}`;
    brCartao.hidden = false;
  }

  async function mostrarVideo(nome) {
    if (!NOME_VIDEO.test(nome) || nome === brMostrado) return;
    brMostrado = nome;
    const arquivo = await pedirArquivo(`/central/acoes/brabo/video/${encodeURIComponent(nome)}`);
    if (brUrl) URL.revokeObjectURL(brUrl);
    brUrl = URL.createObjectURL(arquivo);
    brPlayer.src = brUrl;
    const baixar = el("br-baixar");
    baixar.href = brUrl;
    baixar.download = nome;
    brPronto.hidden = false;
  }

  function mostrarBrabo(r) {
    const limpar = (lista, n) => (Array.isArray(lista) ? lista.slice(0, n).map((x) => String(x).slice(0, 220)) : []);
    const passos = limpar(r && r.passos, 14);
    escreverEm(brPassos, passos.length ? passos : ["…"], r && r.terminou ? (r.ok ? "ok" : "erro") : "fu");
    escreverEm(brSaida, limpar(r && r.saida, 12), "fu");
    el("br-post").textContent = r && typeof r.post === "string" ? r.post.slice(0, 3000) : "";
    const videos = Array.isArray(r && r.videos) ? r.videos.filter((v) => NOME_VIDEO.test(String(v))).slice(0, 8) : [];
    const lista = el("br-lista");
    if (videos.length > 1 && lista.childElementCount !== videos.length) {
      lista.replaceChildren(...videos.map((nome, i) => {
        const b = document.createElement("button");
        b.type = "button";
        b.textContent = `▶ ${i + 1}`;
        b.title = nome;
        b.addEventListener("click", () => mostrarVideo(nome).catch(() => { brMostrado = ""; }));
        return b;
      }));
      if (!r.terminou && !brMostrado) mostrarVideo(videos[videos.length - 1]).catch(() => { brMostrado = ""; });
    } else if (videos.length <= 1) {
      lista.replaceChildren();
    }
    if (r && r.ok && typeof r.video === "string" && r.video) {
      mostrarVideo(r.video).catch(() => { brMostrado = ""; escreverEm(brSaida, ["⚠️ Não consegui abrir o vídeo pronto."], "erro"); });
    }
  }

  async function acompanharBrabo() {
    try {
      const r = await pedir("/central/acoes/brabo");
      mostrarBrabo(r);
      if (r && r.terminou) {
        clearInterval(brEsperando);
        brEsperando = null;
        el("br-revisar").disabled = false;
      }
    } catch (e) {
      escreverEm(brPassos, erroDe(e), "erro");
    }
  }

  async function confirmarBrabo(botao) {
    if (!brPedido) return;
    botao.disabled = true;
    try {
      const r = await enviar("/central/acoes/brabo", brPedido);
      brCartao.hidden = true;
      brPronto.hidden = true;
      brMostrado = "";
      el("br-revisar").disabled = true;
      mostrarBrabo(r);
      document.dispatchEvent(new Event("jarvis:acao"));
      if (!brEsperando) brEsperando = setInterval(acompanharBrabo, 5000);
    } catch (e) {
      const motivo = e instanceof Error && e.message.startsWith("Ação desligada")
        ? "O apresentador está ocupado com outro vídeo (ou JARVIS_CORTES_SAIDA não está no .env)."
        : (e instanceof Error ? e.message : "falhou");
      escreverEm(brPassos, [`⚠️ ${motivo}`], "erro");
    } finally {
      botao.disabled = false;
    }
  }

  brPersonagem.addEventListener("change", trocarPersonagem);
  el("br-atualizar").addEventListener("click", carregarCortesBrabo);
  el("br-revisar").addEventListener("click", revisarBrabo);
  el("br-confirmar").addEventListener("click", (e) => confirmarBrabo(e.currentTarget));
  el("br-cancelar").addEventListener("click", () => { brCartao.hidden = true; });
  for (const id of ["br-tema", "br-corte", "br-quantos", "br-arroba", "br-dupla"]) {
    el(id).addEventListener("input", () => { brCartao.hidden = true; });
  }
})();
