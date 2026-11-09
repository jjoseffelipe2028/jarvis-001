#!/usr/bin/env bash
# Inicia a API Java carregando o .env da RAIZ do projeto.
# Nao cria o .env e nunca imprime o token.
set -e
cd "$(dirname "$0")"

if [ ! -f ../../.env ]; then
  echo "ERRO: arquivo .env nao encontrado na raiz do projeto (../../.env)." >&2
  echo "Crie-o voce mesmo com JARVIS_TOKEN=<valor longo e aleatorio>. Este script nao cria o arquivo." >&2
  exit 1
fi

set -a
. ../../.env
set +a

# Log de auditoria em arquivo (Ticket 010). O .env pode trocar o caminho.
export JARVIS_LOG="${JARVIS_LOG:-logs/bracos.log}"

./mvnw spring-boot:run
