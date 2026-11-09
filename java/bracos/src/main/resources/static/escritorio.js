// Central v2 (Ticket 016): escritório pixel dos agentes. 🔒
// - Tudo desenhado em código num <canvas>: nenhuma imagem, fonte ou biblioteca de fora.
// - Texto na tela só com textContent.
// - Não chama a API sozinho: usa pedir() da central.js (crachá HttpOnly, mesma origem).
// - Só mostra dado real. O que ainda não tem rota na Central aparece como "em breve".
// - 001 e 002 ficam na nuvem: a skill só PREPARA o pedido no cartão de aceite.
"use strict";

(() => {
  const W = 256, H = 144;
  const cv = document.getElementById("esc-cena");
  const camada = document.getElementById("esc-camada");
  const elLog = document.getElementById("esc-log");
  const elSkills = document.getElementById("esc-skills");
  if (!cv || !camada || !elLog || !elSkills) return;
  const g = cv.getContext("2d");
  let t = 0;

  const r = (x, y, w, h, c) => { g.fillStyle = c; g.fillRect(Math.round(x), Math.round(y), w, h); };
  const espera = (ms) => new Promise((ok) => setTimeout(ok, ms));
  const hhmm = () => { const d = new Date(); return String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0"); };

  // ---------- Sprites: cada letra é 1 pixel ----------
  const HUMANO_FRENTE = [
    "...hhhh...", "..hhhhhh..", "..hssssh..", "..sesses..", "..ssssss..", "...ssss...",
    "..cccccc..", ".cccccccc.", ".cccccccc.", ".sccccccs.", ".sccccccs.", "..cccccc..", "..pppppp.."];
  const PERNAS = [
    ["..pp..pp..", "..pp..pp..", "..pp..pp..", "..bb..bb.."],
    ["..pp..pp..", "..pp...pp.", ".pp....pp.", ".bb.....bb"],
    ["..pp..pp..", ".pp...pp..", ".pp....pp.", "bb.....bb."]];
  const HUMANO_COSTAS = [
    "...hhhh...", "..hhhhhh..", "..hhhhhh..", "..hhhhhh..", "..shhhhs..", "...ssss...",
    "..cccccc..", ".cccccccc.", "sccccccccs", ".cccccccc.", "..cccccc..", "..cccccc.."];
  const ROBO_FRENTE = [
    "....aa....", ".....m....", "..mmmmmm..", ".mmmmmmmm.", ".mvvvvvvm.", ".mmmmmmmm.", "..mmmmmm..",
    "....mm....", "..cccccc..", ".mccllccm.", ".mccllccm.", "..cccccc..", "...m..m...", "...m..m...", "..mm..mm.."];
  const ROBO_COSTAS = [
    "....aa....", ".....m....", "..mmmmmm..", ".mmmmmmmm.", ".mmmmmmmm.", ".mmmmmmmm.", "..mmmmmm..",
    "....mm....", "..cccccc..", "mccccccccm", ".cccccccc.", "..cccccc.."];

  function comFone(mapa, fone) { // o 002 usa headset
    if (!fone) return mapa;
    const m = mapa.slice();
    m[1] = ".khhhhhhk.";
    m[2] = mapa === HUMANO_COSTAS ? ".khhhhhhk." : ".khsssshk.";
    return m;
  }
  function spr(ctx, mapa, pal, x, y) {
    for (let i = 0; i < mapa.length; i++) {
      for (let j = 0; j < mapa[i].length; j++) {
        const cor = pal[mapa[i][j]];
        if (cor) { ctx.fillStyle = cor; ctx.fillRect(Math.round(x + j), Math.round(y + i), 1, 1); }
      }
    }
  }

  // ---------- Agentes ----------
  const ORDEM = ["001", "jarvis", "002"];
  const MESA = { "001": 60, "jarvis": 128, "002": 196 };
  const PAL = {
    "001": { h: "#4a2c1d", s: "#f2c49b", e: "#1b1b1b", c: "#e07a55", p: "#2b3150", b: "#151515" },
    "002": { h: "#1e1b2e", s: "#c98b5e", e: "#101010", c: "#5b8def", p: "#262a3d", b: "#121212", k: "#c8d0e6" },
    "jarvis": { a: "#ff4d6d", m: "#8796ad", v: "#4dfff0", c: "#2c3b55", l: "#4dfff0" },
  };
  const COR = { "001": "#e07a55", "002": "#5b8def", "jarvis": "#4dfff0" };
  const NOME = { "001": "001 · Claude", "002": "002 · Gemini", "jarvis": "JARVIS" };

  const agentes = {};
  for (const id of ORDEM) {
    const tag = document.createElement("div");
    tag.className = "esc-tag esc-" + id;
    tag.textContent = NOME[id];
    const balao = document.createElement("div");
    balao.className = "esc-balao";
    balao.hidden = true;
    camada.append(tag, balao);
    agentes[id] = {
      id, robo: id === "jarvis", pal: PAL[id], x: 280, y: 112, caminho: [], fim: null,
      sentado: false, passo: 0, ocupado: false, trabalhaAte: 0, falaAte: 0, fila: Promise.resolve(), tag, balao,
    };
  }
  const J = agentes.jarvis, A1 = agentes["001"], A2 = agentes["002"];

  function andar(a, pontos) {
    a.sentado = false;
    return new Promise((ok) => { a.caminho = pontos.slice(); a.fim = ok; });
  }
  function levantar(a) {
    if (!a.sentado) return;
    a.sentado = false; a.x = MESA[a.id] + 12; a.y = 92;
  }
  async function irPara(a, x, y) {
    levantar(a);
    await andar(a, [{ x: a.x, y: 112 }, { x, y: 112 }, { x, y }]);
  }
  async function voltarParaMesa(a) {
    await irPara(a, MESA[a.id] + 12, 92);
    a.sentado = true; a.x = MESA[a.id];
  }
  function dizer(a, texto, ms = 2800) {
    a.balao.textContent = String(texto).slice(0, 60);
    a.balao.hidden = false;
    a.balao.classList.remove("pop");
    void a.balao.offsetWidth; // reinicia a animação do balão
    a.balao.classList.add("pop");
    a.falaAte = performance.now() + ms;
  }
  function tarefa(a, fn) {
    a.fila = a.fila.then(async () => {
      a.ocupado = true;
      try { await fn(); } catch { /* um erro não trava a fila */ } finally { a.ocupado = false; }
    });
    return a.fila;
  }
  function narrar(texto) {
    const li = document.createElement("li");
    li.textContent = hhmm() + " · " + String(texto).slice(0, 160);
    elLog.prepend(li);
    while (elLog.children.length > 7) elLog.lastChild.remove();
  }

  // ---------- Cenário ----------
  function semente(n) { let s = n; return () => (s = (s * 9301 + 49297) % 233280) / 233280; }
  function predios(x0, largura, seed) {
    const rnd = semente(seed), lista = [];
    let x = x0;
    while (x < x0 + largura) {
      const w = 6 + Math.floor(rnd() * 8), h = 8 + Math.floor(rnd() * 16), janelas = [];
      for (let jy = 2; jy < h - 1; jy += 3) for (let jx = 1; jx < w - 1; jx += 2) janelas.push({ jx, jy, acesa: rnd() > 0.55 });
      lista.push({ x, w, h, janelas, cor: rnd() > 0.5 ? "#141a33" : "#10152b" });
      x += w + 1;
    }
    return lista;
  }
  const JANELAS = [{ x: 22, w: 70, b: predios(22, 70, 7) }, { x: 164, w: 70, b: predios(164, 70, 19) }];
  const ESTRELAS = Array.from({ length: 40 }, (_, i) => { const s = semente(i + 3); return { x: s() * W, y: s() * 26 + 6, f: s() * 100 }; });

  function ceu(hora) {
    if (hora >= 6 && hora < 17) return ["#6fb6ff", "#8cc8ff", "#a9d8ff", "#c7e7ff"];
    if (hora >= 17 && hora < 19) return ["#2b2a5c", "#6b3a6e", "#c25b5b", "#f29b56"];
    return ["#070b22", "#0b1230", "#111a40", "#18234f"];
  }
  function desenharJanela(j, hora) {
    const y = 6, h = 30, faixas = ceu(hora), noite = hora < 6 || hora >= 19;
    r(j.x - 2, y - 2, j.w + 4, h + 4, "#2a3555");
    for (let i = 0; i < 4; i++) r(j.x, y + i * (h / 4), j.w, Math.ceil(h / 4), faixas[i]);
    if (noite) {
      for (const e of ESTRELAS) {
        if (e.x > j.x && e.x < j.x + j.w && e.y < y + 20 && Math.sin((t + e.f * 10) / 30) > -0.3) r(e.x, e.y, 1, 1, "#cfd8ff");
      }
      if (j.x < 100) { r(j.x + 52, y + 4, 6, 6, "#f4f1d0"); r(j.x + 54, y + 4, 4, 4, "#dcd8b0"); }
    } else if (j.x < 100) {
      r(j.x + 50, y + 4, 7, 7, "#fff3a8");
    }
    for (const b of j.b) {
      const by = y + h - b.h, bw = Math.min(b.w, j.x + j.w - b.x);
      if (bw <= 0) continue;
      r(b.x, by, bw, b.h, noite ? b.cor : "#5a6f94");
      for (const jn of b.janelas) {
        if (jn.jx >= bw) continue;
        const acesa = jn.acesa && Math.sin(t / 90 + jn.jx * 3 + jn.jy) > -0.9;
        r(b.x + jn.jx, by + jn.jy, 1, 1, acesa ? (noite ? "#ffd479" : "#9fc4ff") : (noite ? "#1c2340" : "#4a5d80"));
      }
    }
    r(j.x + j.w / 2 - 1, y, 2, h, "#2a3555");
    r(j.x, y + 14, j.w, 2, "#2a3555");
    r(j.x - 3, y + h + 2, j.w + 6, 2, "#33406a");
  }
  function desenharTelaParede() {
    const x = 106, y = 8, w = 44, h = 28;
    r(x - 2, y - 2, w + 4, h + 4, "#0f2a33");
    r(x, y, w, h, "#041319");
    for (let i = 0; i < w; i++) { // batimento do Jarvis
      const k = (i + t / 2) % 22;
      let dy = 0;
      if (k > 9 && k < 11) dy = -7; else if (k >= 11 && k < 12) dy = 5; else if (k >= 12 && k < 13) dy = -2;
      r(x + i, y + 12 + dy, 1, 1, "#4dfff0");
    }
    for (let i = 0; i < 5; i++) {
      const alt = 2 + Math.round(4 + 3 * Math.sin(t / 12 + i));
      r(x + 4 + i * 4, y + h - 3 - alt, 2, alt, i % 2 ? "#a78bfa" : "#4dfff0");
    }
    r(x + 28, y + 19, 12, 1, "#1d6f73"); r(x + 28, y + 22, 9, 1, "#1d6f73");
    if (Math.floor(t / 40) % 2) r(x + 40, y + 3, 2, 2, "#3ddc84");
  }
  function desenharRack() {
    r(4, 28, 16, 50, "#141a2a"); r(4, 28, 16, 1, "#2a3350"); r(5, 29, 14, 48, "#0d1220");
    for (let i = 0; i < 8; i++) {
      const y = 31 + i * 6;
      r(6, y, 12, 4, "#1a2236");
      r(7, y + 1, 1, 1, Math.sin(t / 7 + i * 1.7) > 0 ? "#3ddc84" : "#124a2b");
      r(9, y + 1, 1, 1, Math.sin(t / 5 + i) > 0.6 ? "#4dfff0" : "#0f3b3d");
      r(11, y + 2, 5, 1, "#2a3350");
    }
  }
  function desenharCafe() {
    r(226, 46, 30, 3, "#8a5a3b"); r(226, 49, 30, 20, "#4a2e1c"); r(228, 52, 12, 14, "#3d2617"); r(242, 52, 12, 14, "#3d2617");
    r(232, 32, 12, 14, "#2b2f3a"); r(233, 33, 10, 4, "#1a1d25"); r(234, 34, 2, 2, "#ff4d6d"); r(237, 34, 2, 2, "#3ddc84");
    r(235, 40, 6, 1, "#111"); r(236, 42, 4, 4, "#e8e8e8");
    for (let i = 0; i < 3; i++) r(237 + (i % 2), 41 - ((t / 6 + i * 4) % 10), 1, 1, "rgba(255,255,255,.5)");
    r(247, 38, 5, 8, "#2e7d4f"); r(248, 35, 3, 4, "#3ddc84"); r(246, 36, 2, 2, "#3ddc84");
  }
  function desenharPlanta(x, y) {
    const b = Math.round(Math.sin(t / 40));
    r(x - 4, y, 8, 7, "#7a4a2b"); r(x - 5, y, 10, 2, "#94603a"); r(x - 1, y - 9, 2, 9, "#2e7d4f");
    r(x - 6 + b, y - 8, 5, 3, "#3ddc84"); r(x + 1 + b, y - 11, 5, 3, "#3ddc84");
    r(x - 4 + b, y - 13, 4, 3, "#2fbf72"); r(x + 2 + b, y - 5, 5, 3, "#2fbf72");
  }
  function desenharMesa(id) {
    const dx = MESA[id], a = agentes[id], cor = COR[id], agora = performance.now();
    g.globalAlpha = a.sentado ? 0.14 + 0.04 * Math.sin(t / 15) : 0.05; r(dx - 16, 38, 32, 26, cor); g.globalAlpha = 1;
    r(dx - 24, 60, 48, 1, "#a87250"); r(dx - 24, 61, 48, 3, "#8a5a3b"); r(dx - 24, 64, 48, 2, "#6e4529");
    r(dx - 24, 66, 48, 10, "#4a2e1c"); r(dx - 22, 68, 18, 6, "#3d2617"); r(dx + 4, 68, 18, 6, "#3d2617");
    r(dx - 22, 76, 3, 6, "#3a2416"); r(dx + 19, 76, 3, 6, "#3a2416");
    r(dx - 11, 43, 22, 15, "#1a1f2b"); r(dx - 10, 44, 20, 12, "#05080f");
    if (a.sentado && a.trabalhaAte > agora) {
      for (let i = 0; i < 4; i++) r(dx - 9, 45 + i * 2, 4 + ((i * 7 + Math.floor(t / 4)) % 13), 1, cor);
      r(dx - 9, 54, 18, 1, "#1a2236"); r(dx - 9, 54, Math.round(18 * ((t % 60) / 60)), 1, "#3ddc84");
    } else if (a.sentado) {
      g.globalAlpha = 0.7;
      for (let i = 0; i < 5; i++) r(dx - 9 + (i % 2) * 2, 45 + i * 2, 3 + ((i * 5 + Math.floor(t / 30)) % 12), 1, cor);
      g.globalAlpha = 1;
      if (Math.floor(t / 30) % 2) r(dx + 6, 53, 2, 1, "#fff");
    } else if (Math.floor(t / 45) % 2) {
      r(dx - 1, 49, 2, 2, cor);
    }
    r(dx - 2, 58, 4, 2, "#1a1f2b"); r(dx - 5, 59, 10, 1, "#262c3c");
    r(dx - 8, 61, 14, 2, "#cfd5e2"); r(dx + 9, 61, 2, 2, "#cfd5e2");
    if (id === "001") { r(dx - 21, 57, 4, 4, "#f0f0f0"); r(dx - 17, 58, 1, 2, "#f0f0f0"); r(dx - 20, 58, 2, 1, "#6b3f22"); }
    if (id === "002") { r(dx + 16, 50, 1, 11, "#333a4d"); r(dx + 13, 49, 6, 2, "#f5b942"); g.globalAlpha = 0.15; r(dx + 10, 51, 12, 10, "#f5b942"); g.globalAlpha = 1; }
    if (id === "jarvis") { r(dx - 21, 55, 6, 6, "#1a2236"); r(dx - 19, 57, 2, 2, Math.sin(t / 10) > 0 ? "#4dfff0" : "#1d6f73"); }
  }
  function desenharCadeira(dx) {
    r(dx - 7, 74, 14, 8, "#232a3d"); r(dx - 7, 74, 14, 1, "#323b57"); r(dx - 8, 82, 16, 3, "#1b2131");
    r(dx - 1, 85, 2, 4, "#0e121c"); r(dx - 6, 89, 12, 1, "#0e121c"); r(dx - 6, 90, 2, 1, "#000"); r(dx + 4, 90, 2, 1, "#000");
  }
  function desenharAgente(a) {
    if (a.sentado) {
      spr(g, a.robo ? ROBO_COSTAS : comFone(HUMANO_COSTAS, a.id === "002"), a.pal, a.x - 5, 63);
      const ritmo = a.trabalhaAte > performance.now() ? 6 : 20;
      if (!a.robo && Math.floor(t / ritmo) % 2) { r(a.x - 6, 70, 1, 1, a.pal.s); r(a.x + 5, 71, 1, 1, a.pal.s); }
      return;
    }
    const andando = a.caminho.length > 0;
    const quadro = andando ? [0, 1, 0, 2][Math.floor(a.passo / 4) % 4] : 0;
    g.globalAlpha = 0.35; r(a.x - 5, a.y - 1, 10, 2, "#000"); g.globalAlpha = 1;
    if (a.robo) {
      const flutua = Math.round(Math.sin(t / 10));
      spr(g, ROBO_FRENTE, a.pal, a.x - 5, a.y - 17 + flutua);
      r(a.x - 4 + (Math.floor(t / 6) % 8), a.y - 13 + flutua, 1, 1, "#ffffff");
      g.globalAlpha = 0.5; r(a.x - 2, a.y - 2 + flutua, 4, 1, "#4dfff0"); g.globalAlpha = 1;
    } else {
      const topo = a.y - 17 + (andando && quadro !== 0 ? -1 : 0);
      spr(g, comFone(HUMANO_FRENTE, a.id === "002"), a.pal, a.x - 5, topo);
      spr(g, PERNAS[quadro], a.pal, a.x - 5, topo + 13);
    }
  }
  function desenhar() {
    const hora = new Date().getHours();
    r(0, 0, W, 56, "#121a2e");
    for (let x = 0; x < W; x += 32) r(x, 0, 1, 54, "#16203a");
    r(0, 0, W, 2, "#0b1020"); r(0, 52, W, 2, "#1a2440"); r(0, 54, W, 2, "#0b1020");
    for (const j of JANELAS) desenharJanela(j, hora);
    desenharTelaParede();
    for (let y = 56; y < H; y += 8) for (let x = 0; x < W; x += 8) r(x, y, 8, 8, ((x + y) / 8) % 2 ? "#1a2236" : "#1d263b");
    r(0, 56, W, 1, "#0b1020");
    r(84, 98, 88, 34, "#2b1f45"); r(86, 100, 84, 30, "#33255a"); r(90, 104, 76, 22, "#2b1f45");
    for (let i = 0; i < 8; i++) r(94 + i * 9, 114, 4, 2, "#4b3a80");
    desenharRack();
    desenharCafe();
    for (const id of ORDEM) {
      desenharMesa(id);
      if (agentes[id].sentado) desenharAgente(agentes[id]);
      desenharCadeira(MESA[id]);
    }
    desenharPlanta(12, 124);
    desenharPlanta(246, 128);
    Object.values(agentes).filter((a) => !a.sentado).sort((p, q) => p.y - q.y).forEach(desenharAgente);
    if (hora < 6 || hora >= 19) { g.fillStyle = "rgba(20,10,60,.10)"; g.fillRect(0, 0, W, H); }
  }
  function mover() {
    for (const a of Object.values(agentes)) {
      if (!a.caminho.length) continue;
      const alvo = a.caminho[0], vx = alvo.x - a.x, vy = alvo.y - a.y, d = Math.hypot(vx, vy), v = 0.75;
      if (d <= v) {
        a.x = alvo.x; a.y = alvo.y; a.caminho.shift();
        if (!a.caminho.length && a.fim) { const fim = a.fim; a.fim = null; fim(); }
      } else {
        a.x += (vx / d) * v; a.y += (vy / d) * v; a.passo += v;
      }
    }
  }
  function posicionarTextos() {
    const agora = performance.now();
    for (const a of Object.values(agentes)) {
      const cabeca = a.sentado ? 63 : a.y - 17;
      const x = (a.x / W) * 100 + "%";
      a.tag.style.left = x; a.tag.style.top = ((cabeca - 1) / H) * 100 + "%";
      a.balao.style.left = x; a.balao.style.top = ((cabeca - 6) / H) * 100 + "%";
      a.tag.hidden = a.x > W;
      if (a.falaAte && agora > a.falaAte) { a.balao.hidden = true; a.falaAte = 0; }
    }
  }
  function quadro() { t++; mover(); desenhar(); posicionarTextos(); requestAnimationFrame(quadro); }

  // ---------- Skills ----------
  async function rotinaReal(rota, inicio, resumir) {
    dizer(J, inicio, 8000);
    J.trabalhaAte = performance.now() + 8000;
    try {
      const resposta = await pedir(rota); // central.js: crachá + mesma origem
      const { fala, linha, saida } = resumir(resposta);
      dizer(J, fala, 4000); narrar(linha); escrever(saida);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "falhou";
      dizer(J, "⚠️ Não consegui", 3500); narrar("Jarvis: " + msg); escrever(["⚠️ " + msg], "erro");
    } finally {
      J.trabalhaAte = 0;
    }
  }
  function emBreve(a, oQue) {
    dizer(a, "Em breve 🔜", 2600);
    narrar(oQue + " chega com ticket próprio.");
  }
  function prepararNuvem(a, texto) {
    // Só prepara: o envio continua sendo o "Aceitar" do cartão (que copia para o Claude Code)
    pedidoDoEscritorio = a;
    document.getElementById("nuvem").hidden = false;
    document.getElementById("nuvem-texto").value = texto;
    prepararPedido();
    document.getElementById("cartao").scrollIntoView({ behavior: "smooth", block: "center" });
    dizer(a, "Pedido no cartão: Aceitar? 🌐", 6000);
    narrar(NOME[a.id] + " preparou um pedido. Nada sai sem o seu Aceitar.");
  }
  let pedidoDoEscritorio = null;
  document.getElementById("bt-aceitar").addEventListener("click", () => {
    if (!pedidoDoEscritorio) return;
    dizer(pedidoDoEscritorio, "Copiado! Cola no Claude Code 📋", 3500);
    narrar("Aceito: pedido copiado. Nada foi enviado sozinho.");
    pedidoDoEscritorio = null;
  });
  document.getElementById("bt-recusar").addEventListener("click", () => {
    if (!pedidoDoEscritorio) return;
    dizer(pedidoDoEscritorio, "Beleza, nada saiu 👍", 3000);
    narrar("Recusado: nada saiu do PC.");
    pedidoDoEscritorio = null;
  });

  const SKILLS = [
    { id: "jarvis", papel: "Assistente local · roda no seu PC", onde: "💻 offline · nada sai do PC", lista: [
      { ic: "🕐", nome: "Hora", st: "ar", rodar: async () => { dizer(J, "São " + hhmm() + " 🕐"); narrar("Jarvis: são " + hhmm() + "."); } },
      { ic: "📋", nome: "Pendências", st: "ar", rodar: () => rotinaReal("/central/acoes/pendencias", "Lendo o cofre... 📂", (res) => {
          const lista = Array.isArray(res && res.pendencias) ? res.pendencias : [];
          const total = Number.isInteger(res && res.total) ? res.total : lista.length;
          const primeira = lista.length ? " A primeira: " + String(lista[0] && lista[0].texto).slice(0, 80) : "";
          return { fala: total + " pendência(s) 📋", linha: "Jarvis leu o cofre: " + total + " pendência(s)." + primeira, saida: formatarPendencias(res) };
        }) },
      { ic: "🌿", nome: "Git status", st: "ar", rodar: () => rotinaReal("/central/acoes/git-status", "Olhando o Git... 🌿", (s) => {
          const limpo = s && s.limpo === true;
          return { fala: limpo ? "Tudo commitado ✅" : "Tem trabalho sem commit 🌿",
                   linha: "Jarvis: branch " + String(s && s.branch) + (limpo ? " em dia." : " com mudanças sem commit."), saida: formatarGit(s) };
        }) },
      { ic: "📝", nome: "Daily do dia", st: "breve", rodar: async () => emBreve(J, "O daily pela Central") },
      { ic: "🎬", nome: "Porteiro de vídeo", st: "breve", rodar: async () => emBreve(J, "O porteiro de vídeo pela Central") },
      { ic: "📊", nome: "Relatório de retenção", st: "breve", rodar: async () => emBreve(J, "O relatório de retenção pela Central") },
      { ic: "🎙️", nome: "Narrar em voz alta", st: "breve", rodar: async () => emBreve(J, "A narração por voz") },
    ] },
    { id: "001", papel: "Mentor e engenheiro · planeja e constrói", onde: "🌐 nuvem · só com Aceitar", lista: [
      { ic: "🧭", nome: "Planejar ticket", st: "term", rodar: async () => prepararNuvem(A1, "001, planeja o próximo ticket do Jarvis: plano curto e espera meu OK.") },
      { ic: "☕", nome: "Codar Java", st: "term", rodar: async () => prepararNuvem(A1, "001, bora construir o ticket aprovado, com testes.") },
      { ic: "🔒", nome: "Revisão de segurança", st: "term", rodar: async () => prepararNuvem(A1, "001, faz uma revisão de segurança do que mudou hoje.") },
      { ic: "🎓", nome: "Mentor Java + Cyber", st: "term", rodar: async () => prepararNuvem(A1, "001, qual a minha próxima micro-etapa de estudo (Java ou Cyber)?") },
    ] },
    { id: "002", papel: "Revisor e pesquisador · contesta tudo", onde: "🌐 nuvem · só com Aceitar", lista: [
      { ic: "🥊", nome: "Contestar plano", st: "breve", rodar: async () => emBreve(A2, "O 002 contestando direto pela Central (Fase 7)") },
      { ic: "🔍", nome: "Revisar código", st: "breve", rodar: async () => emBreve(A2, "O 002 revisando direto pela Central (Fase 7)") },
      { ic: "📚", nome: "Pesquisar fontes", st: "breve", rodar: async () => emBreve(A2, "O 002 pesquisando direto pela Central (Fase 7)") },
    ] },
  ];
  const ROTULO = { ar: "no ar", term: "via terminal", breve: "em breve" };

  function avatar(id) {
    const c = document.createElement("canvas");
    c.width = 14; c.height = 14; c.className = "esc-avatar";
    const mapa = id === "jarvis" ? ROBO_FRENTE : comFone(HUMANO_FRENTE, id === "002");
    spr(c.getContext("2d"), mapa.slice(0, 12), PAL[id], 2, 2);
    return c;
  }
  for (const info of SKILLS) {
    const card = document.createElement("section");
    card.className = "esc-card esc-" + info.id;
    const topo = document.createElement("div"); topo.className = "esc-card-topo";
    const textos = document.createElement("div");
    const nome = document.createElement("div"); nome.className = "esc-nome"; nome.textContent = NOME[info.id];
    const papel = document.createElement("div"); papel.className = "esc-papel"; papel.textContent = info.papel;
    textos.append(nome, papel);
    topo.append(avatar(info.id), textos);
    const onde = document.createElement("span"); onde.className = "esc-onde"; onde.textContent = info.onde;
    const lista = document.createElement("div"); lista.className = "esc-lista";
    for (const s of info.lista) {
      const b = document.createElement("button"); b.type = "button"; b.className = "esc-skill";
      const ic = document.createElement("span"); ic.textContent = s.ic;
      const nm = document.createElement("span"); nm.textContent = s.nome;
      const st = document.createElement("span"); st.className = "esc-st " + s.st; st.textContent = ROTULO[s.st];
      b.append(ic, nm, st);
      b.addEventListener("click", () => tarefa(agentes[info.id], s.rodar));
      lista.append(b);
    }
    card.append(topo, onde, lista);
    elSkills.append(card);
  }

  // ---------- Vida no escritório ----------
  async function chegar(a, atraso, fala) {
    await espera(atraso);
    await andar(a, [{ x: 262, y: 112 }, { x: MESA[a.id] + 12, y: 112 }, { x: MESA[a.id] + 12, y: 92 }]);
    a.sentado = true; a.x = MESA[a.id];
    dizer(a, fala); narrar(NOME[a.id] + " chegou e sentou.");
  }
  const h = new Date().getHours();
  const saudacao = h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite";
  tarefa(J, () => chegar(J, 300, saudacao + ", José! 👋"));
  tarefa(A1, () => chegar(A1, 1600, "Bora codar ☕"));
  tarefa(A2, () => chegar(A2, 2900, "Pronto pra contestar 🥊"));

  setInterval(() => { // pausa para o café de vez em quando
    const livres = Object.values(agentes).filter((a) => a.sentado && !a.ocupado);
    if (!livres.length || Math.random() < 0.4) return;
    const a = livres[Math.floor(Math.random() * livres.length)];
    tarefa(a, async () => {
      if (a.robo) { dizer(a, "Recarregando 🔋", 2400); await irPara(a, 30, 92); await espera(2200); }
      else { dizer(a, "Café ☕", 2000); await irPara(a, 238, 76); await espera(1800); dizer(a, "Agora sim 😌", 1800); }
      await voltarParaMesa(a);
    });
  }, 14000);

  narrar("Escritório abrindo...");
  requestAnimationFrame(quadro);
})();
