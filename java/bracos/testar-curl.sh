#!/usr/bin/env bash
# Testa a API Java com 5 casos de curl (porteiro de token + log injection).
# Carrega o .env da RAIZ, nao cria o .env e nunca imprime o token.
# O token vai por stdin (-H @-), nunca como argumento de comando.
set -eu
cd "$(dirname "$0")"

if [ ! -f ../../.env ]; then
  echo "ERRO: arquivo .env nao encontrado na raiz do projeto (../../.env)." >&2
  echo "Crie-o voce mesmo com JARVIS_TOKEN=<valor longo e aleatorio>. Este script nao cria o arquivo." >&2
  exit 1
fi

set -a
. ../../.env
set +a

if [ -z "${JARVIS_TOKEN:-}" ]; then
  echo "ERRO: JARVIS_TOKEN vazio no .env" >&2
  exit 1
fi

BASE="http://127.0.0.1:8080"
CABECALHO_TOKEN="X-Jarvis-Token"
CLIENTE="X-Jarvis-Cliente: python"

# Checa se a API responde antes dos casos
if ! curl -s -o /dev/null -w '%{http_code}' -H "$CLIENTE" "$BASE/acoes/hora" > /dev/null; then
  echo "API fora do ar: rode ./iniciar.sh em outra janela" >&2
  exit 1
fi

acertos=0

# checar numero descricao esperado recebido
checar() {
  local resultado="FALHOU"
  if [ "$3" = "$4" ]; then
    resultado="ok"
    acertos=$((acertos + 1))
  fi
  printf '[%s] %-32s esperado %s  recebido %s  %s\n' "$1" "$2" "$3" "$4" "$resultado"
}

# Caso 1: GET aberto
r=$(curl -s -o /dev/null -w '%{http_code}' -H "$CLIENTE" "$BASE/acoes/hora")
checar 1 "GET  /acoes/hora" 200 "$r"

# Caso 2: POST sem token
r=$(curl -s -o /dev/null -w '%{http_code}' -H "$CLIENTE" \
  -X POST -H "Content-Type: application/json" -d '{}' "$BASE/acoes/nao-existe")
checar 2 "POST /acoes/nao-existe sem token" 401 "$r"

# Caso 3: POST com token errado (por stdin)
r=$(printf '%s: %s\n' "$CABECALHO_TOKEN" "errado-123" | curl -s -o /dev/null -w '%{http_code}' -H "$CLIENTE" \
  -H @- -X POST -H "Content-Type: application/json" -d '{}' "$BASE/acoes/nao-existe")
checar 3 "POST /acoes/nao-existe token errado" 401 "$r"

# Caso 4: POST com token certo (por stdin)
r=$(printf '%s: %s\n' "$CABECALHO_TOKEN" "$JARVIS_TOKEN" | curl -s -o /dev/null -w '%{http_code}' -H "$CLIENTE" \
  -H @- -X POST -H "Content-Type: application/json" -d '{}' "$BASE/acoes/nao-existe")
checar 4 "POST /acoes/nao-existe token certo" 404 "$r"

# Caso 5: URL com quebra de linha (%0d%0a) - o log nao pode gravar linha falsa
echo ">> $(date +%H:%M:%S) caso 5 enviado (procure esta hora no iniciar.sh)"
r=$(curl -s -o /dev/null -w '%{http_code}' -H "$CLIENTE" --path-as-is \
  -X POST -H "Content-Type: application/json" -d '{}' \
  "$BASE/acoes/x%0d%0aacao=x%20resultado=ok")
checar 5 "POST /acoes/x%0d%0a... sem token" 401 "$r"

echo "Resumo: ${acertos}/5 ok. Confira o caso 5 na janela do iniciar.sh."
if [ "$acertos" -ne 5 ]; then
  exit 1
fi
